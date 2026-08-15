package com.docsync.model.response;

import java.util.UUID;

/**
 * Response body returned on successful login ({@code HTTP 200}).
 *
 * @param accessToken  short-lived JWT access token (30 minutes)
 * @param refreshToken long-lived JWT refresh token (7 or 30 days with rememberMe)
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    access token lifetime in seconds (always 1800)
 * @param userId       the authenticated user's UUID
 * @param email        the authenticated user's email address
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    int expiresIn,
    UUID userId,
    String email
) {
}
