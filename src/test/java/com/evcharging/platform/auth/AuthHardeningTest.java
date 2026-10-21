package com.evcharging.platform.auth;

import com.evcharging.platform.TestcontainersConfiguration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthHardeningTest {

    @Value("${local.server.port}")
    int port;

    @Value("${app.jwt.secret}")
    String secret;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String token(String signingSecret, String issuer, Instant issuedAt, Instant expiresAt) {
        var key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject("someone").issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private int reservationsStatus(String bearer) {
        return client().get().uri("/api/v1/reservations")
                .header("Authorization", "Bearer " + bearer)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private int registerStatus(String username, String password) {
        return client().post().uri("/api/v1/auth/register")
                .body(Map.of("username", username, "password", password))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    @Test
    void correctlySignedUnexpiredTokenFromUsIsAccepted() {
        Instant now = Instant.now();
        assertThat(reservationsStatus(token(secret, JwtService.ISSUER, now, now.plus(10, ChronoUnit.MINUTES))))
                .isEqualTo(200);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        String expired = token(secret, JwtService.ISSUER, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        assertThat(reservationsStatus(expired)).isEqualTo(401);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        Instant now = Instant.now();
        String forged = token("a-completely-different-secret-0123456789", JwtService.ISSUER, now, now.plus(10, ChronoUnit.MINUTES));
        assertThat(reservationsStatus(forged)).isEqualTo(401);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        Instant now = Instant.now();
        String wrongIssuer = token(secret, "someone-else", now, now.plus(10, ChronoUnit.MINUTES));
        assertThat(reservationsStatus(wrongIssuer)).isEqualTo(401);
    }

    @Test
    void malformedTokensAreRejected() {
        assertThat(reservationsStatus("garbage")).isEqualTo(401);
        assertThat(reservationsStatus("a.b.c")).isEqualTo(401);
    }

    @Test
    void weakOrInvalidRegistrationsAreRejected() {
        assertThat(registerStatus("validname1", "short1")).isEqualTo(400);          // too short
        assertThat(registerStatus("validname2", "onlyletters")).isEqualTo(400);     // no digit
        assertThat(registerStatus("validname3", "12345678")).isEqualTo(400);        // no letter
        assertThat(registerStatus("ab", "correct-horse-1")).isEqualTo(400);         // username too short
        assertThat(registerStatus("bad name!", "correct-horse-1")).isEqualTo(400);  // bad characters
    }
}