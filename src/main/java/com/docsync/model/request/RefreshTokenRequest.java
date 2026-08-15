package com.docsync.model.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the {@code POST /api/v1/auth/refresh} endpoint.
 *
 * @param refreshToken the JWT refresh token previously issued during login
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token must not be blank")
    String refreshToken
) {
}
