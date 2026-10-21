package com.evcharging.platform.auth;

import com.evcharging.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.auth.rate-limit.max-requests=3")
@Import(TestcontainersConfiguration.class)
class AuthRateLimitTest {

    @Value("${local.server.port}")
    int port;

    private int wrongLogin() {
        return RestClient.create("http://localhost:" + port).post().uri("/api/v1/auth/login")
                .body(Map.of("username", "nobody", "password", "wrong-password-1"))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    @Test
    void loginIsRateLimitedAfterTooManyAttempts() {
        assertThat(wrongLogin()).isEqualTo(401);
        assertThat(wrongLogin()).isEqualTo(401);
        assertThat(wrongLogin()).isEqualTo(401);
        assertThat(wrongLogin()).isEqualTo(429);
        assertThat(wrongLogin()).isEqualTo(429);
    }
}