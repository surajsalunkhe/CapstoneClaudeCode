package com.docsync.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that the per-IP rate limiter correctly returns HTTP 429
 * when the request limit is exceeded (FINDING-021).
 * Uses the {@code test} profile which sets {@code limit-for-period: 1}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "JWT_SECRET=rate-limiter-test-secret-key-min-32-chars!!",
        "spring.flyway.enabled=true",
        "resilience4j.ratelimiter.instances.authRateLimiter.limit-for-period=2",
        "resilience4j.ratelimiter.instances.authRateLimiter.limit-refresh-period=60s",
        "resilience4j.ratelimiter.instances.authRateLimiter.timeout-duration=0s"
    }
)
@AutoConfigureMockMvc
@Tag("integration")
@ActiveProfiles("test")
@Testcontainers
class AuthRateLimiterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("ratelimiterdb")
        .withUsername("test")
        .withPassword("test");

    /**
     * Configures Spring datasource properties to use the Testcontainers PostgreSQL instance.
     *
     * @param registry the dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that requests beyond the rate limit threshold return HTTP 429.
     *
     * @throws Exception if the request fails
     */
    @Test
    void rateLimit_exceedingThreshold_returns429() throws Exception {
        String body = """
            {"email":"ratelimit@example.com","password":"Passw0rd!",
             "confirmPassword":"Passw0rd!","acceptTerms":true}
            """;

        // Send requests up to and including the limit (limit-for-period=2 in test config)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf())
                    .remoteAddress("192.168.100.1"));
        }

        // The next request from the same IP should be rate-limited
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .remoteAddress("192.168.100.1"))
            .andExpect(status().isTooManyRequests());
    }
}
