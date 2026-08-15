package com.docsync.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the {@code POST /api/v1/auth/login} endpoint.
 *
 * @param email      the user's registered email address
 * @param password   the user's password (validated server-side against BCrypt hash)
 * @param rememberMe when {@code true}, the refresh token uses the extended "remember me"
 *                   expiry (30 days instead of 7 days, per ADR-001)
 */
public record LoginRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    String email,

    @NotBlank(message = "Password must not be blank")
    String password,

    boolean rememberMe
) {
}
