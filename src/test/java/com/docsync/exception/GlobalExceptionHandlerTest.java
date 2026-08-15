package com.docsync.exception;

import com.docsync.controller.AuthController;
import com.docsync.service.AuthService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link GlobalExceptionHandler} covering all exception-to-HTTP-status mappings
 * defined in architecture section 11 (RC-008 — FINDING-019).
 */
@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.docsync\\.config\\..*"
        )
    }
)
@Import({GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private static final String REGISTER_BODY = """
        {"email":"user@example.com","password":"Passw0rd!",
         "confirmPassword":"Passw0rd!","acceptTerms":true}
        """;

    /**
     * Verifies that DuplicateEmailException maps to HTTP 409.
     *
     * @throws Exception if the request fails
     */
    @Test
    void duplicateEmailException_returns409() throws Exception {
        when(authService.register(any())).thenThrow(
            new DuplicateEmailException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    /**
     * Verifies that InvalidCredentialsException maps to HTTP 401.
     *
     * @throws Exception if the request fails
     */
    @Test
    void invalidCredentialsException_returns401() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Invalid email or password"));

        String loginBody = """
            {"email":"user@example.com","password":"WrongPass!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that AccountLockedException maps to HTTP 403.
     *
     * @throws Exception if the request fails
     */
    @Test
    void accountLockedException_returns403() throws Exception {
        when(authService.login(any())).thenThrow(new AccountLockedException("Account locked"));

        String loginBody = """
            {"email":"user@example.com","password":"Passw0rd!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that InvalidTokenException maps to HTTP 401.
     *
     * @throws Exception if the request fails
     */
    @Test
    void invalidTokenException_returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(
            new InvalidTokenException("Invalid or revoked token"));

        String body = """
            {"refreshToken":"bad.token"}
            """;
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that TokenExpiredException maps to HTTP 401.
     *
     * @throws Exception if the request fails
     */
    @Test
    void tokenExpiredException_returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new TokenExpiredException("Token has expired"));

        String body = """
            {"refreshToken":"expired.token"}
            """;
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that bean validation failures (MethodArgumentNotValidException) map to HTTP 400.
     *
     * @throws Exception if the request fails
     */
    @Test
    void methodArgumentNotValidException_returns400() throws Exception {
        String invalidBody = """
            {"email":"not-an-email","password":"weak","confirmPassword":"weak","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    /**
     * Verifies that DataAccessException maps to HTTP 503.
     *
     * @throws Exception if the request fails
     */
    @Test
    void dataAccessException_returns503() throws Exception {
        when(authService.register(any())).thenThrow(
            new DataAccessResourceFailureException("DB unavailable"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that DisabledException maps to HTTP 403 (FINDING-010).
     *
     * @throws Exception if the request fails
     */
    @Test
    void disabledException_returns403() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("Account is disabled"));

        String loginBody = """
            {"email":"user@example.com","password":"Passw0rd!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that unhandled exceptions map to HTTP 500.
     *
     * @throws Exception if the request fails
     */
    @Test
    void unexpectedException_returns500() throws Exception {
        when(authService.register(any())).thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    /**
     * Verifies that RequestNotPermitted (rate limit) maps to HTTP 429.
     *
     * @throws Exception if the request fails
     */
    @Test
    void requestNotPermitted_returns429() throws Exception {
        when(authService.register(any())).thenThrow(
            RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("test")));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false));
    }
}
