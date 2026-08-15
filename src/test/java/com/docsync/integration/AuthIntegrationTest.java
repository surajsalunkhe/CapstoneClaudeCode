package com.docsync.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the authentication API using a real PostgreSQL database
 * (Testcontainers). All tests run within a single Spring application context.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "JWT_SECRET=integration-test-secret-key-min-32-chars!!",
        "spring.flyway.enabled=true"
    }
)
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
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

    private static final String VALID_PASSWORD = "Passw0rd!";
    private static final String EMAIL = "integration@example.com";

    private static final String REGISTER_BODY = """
        {"email":"%s","password":"%s","confirmPassword":"%s","acceptTerms":true}
        """.formatted(EMAIL, VALID_PASSWORD, VALID_PASSWORD);

    /**
     * Verifies successful user registration returns HTTP 201.
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_validData_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY)
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    /**
     * Verifies the full registration-login-logout cycle (AC-001, AC-004, AC-007).
     *
     * @throws Exception if the request fails
     */
    @Test
    void fullCycle_registerLoginLogout() throws Exception {
        String uniqueEmail = "cycle@example.com";
        String registerBody = """
            {"email":"%s","password":"%s","confirmPassword":"%s","acceptTerms":true}
            """.formatted(uniqueEmail, VALID_PASSWORD, VALID_PASSWORD);

        // Register
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody)
                .with(csrf()))
            .andExpect(status().isCreated());

        // Login
        String loginBody = """
            {"email":"%s","password":"%s","rememberMe":false}
            """.formatted(uniqueEmail, VALID_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.accessToken");

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    /**
     * Verifies that registering with a duplicate email returns HTTP 409 (AC-002).
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String uniqueEmail = "dup@example.com";
        String body = """
            {"email":"%s","password":"%s","confirmPassword":"%s","acceptTerms":true}
            """.formatted(uniqueEmail, VALID_PASSWORD, VALID_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }
}
