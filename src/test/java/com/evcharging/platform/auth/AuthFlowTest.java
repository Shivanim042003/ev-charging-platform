package com.evcharging.platform.auth;

import com.evcharging.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private int status(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> response.getStatusCode().value());
    }

    private Map<?, ?> json(RestClient.RequestHeadersSpec<?> spec) {
        return spec.retrieve().body(Map.class);
    }

    private String registerAndLogin(String username) {
        Map<String, String> creds = Map.of("username", username, "password", "correct-horse-1");
        assertThat(status(client().post().uri("/api/v1/auth/register").body(creds))).isEqualTo(201);
        Map<?, ?> login = json(client().post().uri("/api/v1/auth/login").body(creds));
        return login.get("token").toString();
    }

    @Test
    void protectedEndpointsNeedTokenAndOwnershipIsEnforced() {
        // no token
        assertThat(status(client().get().uri("/api/v1/reservations"))).isEqualTo(401);
        assertThat(status(client().post().uri("/api/v1/stations")
                .body(Map.of("name", "x", "address", "y")))).isEqualTo(401);
        // public reads stay open
        assertThat(status(client().get().uri("/api/v1/stations"))).isEqualTo(200);

        // register and login
        String alice = "alice" + UUID.randomUUID().toString().substring(0, 8);
        String bob = "bob" + UUID.randomUUID().toString().substring(0, 8);
        String aliceToken = registerAndLogin(alice);
        String bobToken = registerAndLogin(bob);

        // duplicate username and wrong password
        Map<String, String> creds = Map.of("username", alice, "password", "correct-horse-1");
        assertThat(status(client().post().uri("/api/v1/auth/register").body(creds))).isEqualTo(409);
        assertThat(status(client().post().uri("/api/v1/auth/login")
                .body(Map.of("username", alice, "password", "wrong-password-1")))).isEqualTo(401);

        // garbage token
        assertThat(status(client().get().uri("/api/v1/reservations")
                .header("Authorization", "Bearer not.a.token"))).isEqualTo(401);

        // alice creates a station and connector, then books; the body userRef is ignored
        Map<?, ?> station = json(client().post().uri("/api/v1/stations")
                .header("Authorization", "Bearer " + aliceToken)
                .body(Map.of("name", "Auth Test", "address", "1 Test St", "latitude", 44.9, "longitude", -93.2)));
        Map<?, ?> connector = json(client().post().uri("/api/v1/stations/" + station.get("id") + "/connectors")
                .header("Authorization", "Bearer " + aliceToken)
                .body(Map.of("connectorNumber", "A1", "powerKw", 50)));
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).truncatedTo(ChronoUnit.HOURS);
        Map<?, ?> booking = json(client().post().uri("/api/v1/reservations")
                .header("Authorization", "Bearer " + aliceToken)
                .body(Map.of("connectorId", connector.get("id").toString(), "userRef", "mallory",
                        "startTime", start.toString(), "endTime", start.plusHours(1).toString())));
        assertThat(booking.get("userRef")).isEqualTo(alice);
        String id = booking.get("id").toString();

        // bob cannot cancel it (404) and does not see it; alice can cancel it
        assertThat(status(client().patch().uri("/api/v1/reservations/" + id + "/cancel")
                .header("Authorization", "Bearer " + bobToken))).isEqualTo(404);
        java.util.List<?> bobsReservations = client().get().uri("/api/v1/reservations")
                .header("Authorization", "Bearer " + bobToken)
                .retrieve().body(java.util.List.class);
        assertThat(bobsReservations).isEmpty();
        Map<?, ?> cancelled = json(client().patch().uri("/api/v1/reservations/" + id + "/cancel")
                .header("Authorization", "Bearer " + aliceToken));
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
    }
}