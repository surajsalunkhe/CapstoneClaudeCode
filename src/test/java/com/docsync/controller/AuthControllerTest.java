package com.docsync.controller;

import com.docsync.exception.AccountLockedException;
import com.docsync.exception.DuplicateEmailException;
import com.docsync.exception.GlobalExceptionHandler;
import com.docsync.exception.InvalidCredentialsException;
import com.docsync.exception.TokenExpiredException;
import com.docsync.model.response.LoginResponse;
import com.docsync.model.response.RefreshResponse;
import com.docsync.model.response.RegisterResponse;
import com.docsync.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@link AuthController} using {@code @WebMvcTest}.
 * Security auto-configuration is excluded so tests focus purely on controller and handler logic.
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    /**
     * Verifies that a valid registration request returns HTTP 201 with success body.
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterResponse resp = new RegisterResponse(UUID.randomUUID(), "user@example.com",
            Instant.now());
        when(authService.register(any())).thenReturn(resp);

        String body = """
            {"email":"user@example.com","password":"Passw0rd!",
             "confirmPassword":"Passw0rd!","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registration successful"));
    }

    /**
     * Verifies that an invalid email returns HTTP 400.
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_invalidEmail_returns400() throws Exception {
        String body = """
            {"email":"not-an-email","password":"Passw0rd!",
             "confirmPassword":"Passw0rd!","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that a weak password returns HTTP 400.
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_weakPassword_returns400() throws Exception {
        String body = """
            {"email":"user@example.com","password":"weak",
             "confirmPassword":"weak","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that a duplicate email returns HTTP 409.
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any())).thenThrow(
            new DuplicateEmailException("Email already registered"));

        String body = """
            {"email":"user@example.com","password":"Passw0rd!",
             "confirmPassword":"Passw0rd!","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that mismatched password and confirmPassword return HTTP 400 (CR-002 / AC-003).
     *
     * @throws Exception if the request fails
     */
    @Test
    void register_passwordMismatch_returns400() throws Exception {
        when(authService.register(any())).thenThrow(
            new IllegalArgumentException("Passwords do not match"));

        String body = """
            {"email":"user@example.com","password":"Passw0rd!",
             "confirmPassword":"Different1!","acceptTerms":true}
            """;
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    /**
     * Verifies that valid credentials return HTTP 200 with tokens.
     *
     * @throws Exception if the request fails
     */
    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginResponse loginResp = new LoginResponse(
            "access.token", "refresh.token", "Bearer", 1800,
            UUID.randomUUID(), "user@example.com");
        when(authService.login(any())).thenReturn(loginResp);

        String body = """
            {"email":"user@example.com","password":"Passw0rd!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    /**
     * Verifies that invalid credentials return HTTP 401.
     *
     * @throws Exception if the request fails
     */
    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Invalid email or password"));

        String body = """
            {"email":"user@example.com","password":"WrongPass!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that a locked account returns HTTP 403.
     *
     * @throws Exception if the request fails
     */
    @Test
    void login_lockedAccount_returns403() throws Exception {
        when(authService.login(any())).thenThrow(
            new AccountLockedException("Account locked due to too many failed attempts."));

        String body = """
            {"email":"user@example.com","password":"Passw0rd!","rememberMe":false}
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Verifies that a valid logout returns HTTP 200.
     *
     * @throws Exception if the request fails
     */
    @Test
    void logout_validToken_returns200() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer valid.token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    /**
     * Verifies that logout without Authorization header returns HTTP 200.
     *
     * @throws Exception if the request fails
     */
    @Test
    void logout_invalidToken_returns200() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk());
    }

    /**
     * Verifies that a valid refresh token returns HTTP 200 with a new access token.
     *
     * @throws Exception if the request fails
     */
    @Test
    void refresh_validRefreshToken_returns200() throws Exception {
        RefreshResponse refreshResp = new RefreshResponse("new.access.token", 1800);
        when(authService.refresh(any())).thenReturn(refreshResp);

        String body = """
            {"refreshToken":"valid.refresh.token"}
            """;
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    /**
     * Verifies that an expired refresh token returns HTTP 401.
     *
     * @throws Exception if the request fails
     */
    @Test
    void refresh_expiredToken_returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new TokenExpiredException("Token has expired"));

        String body = """
            {"refreshToken":"expired.refresh.token"}
            """;
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }
}
