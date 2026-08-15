package com.docsync.controller;

import com.docsync.model.request.LoginRequest;
import com.docsync.model.request.RefreshTokenRequest;
import com.docsync.model.request.RegisterRequest;
import com.docsync.model.response.ApiResponse;
import com.docsync.model.response.LoginResponse;
import com.docsync.model.response.RefreshResponse;
import com.docsync.model.response.RegisterResponse;
import com.docsync.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * Accepts HTTP requests, delegates all business logic to {@link AuthService},
 * and returns standardized {@link ApiResponse} envelopes.
 * Contains no business logic.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Constructs an {@code AuthController} with the required service dependency.
     *
     * @param authService the authentication service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * @param request validated registration request body
     * @return HTTP 201 with {@link RegisterResponse} on success
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Registration successful", response));
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * @param request validated login request body
     * @return HTTP 200 with {@link LoginResponse} on success
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Logs out the authenticated user by invalidating their access token.
     *
     * @param request the HTTP servlet request (used to extract the Bearer token)
     * @return HTTP 200 with logout confirmation on success
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader.substring(7)
            : null;
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Issues a new access token in exchange for a valid refresh token.
     *
     * @param request validated refresh token request body
     * @return HTTP 200 with {@link RefreshResponse} on success
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        RefreshResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }
}
