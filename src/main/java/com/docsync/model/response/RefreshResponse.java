package com.docsync.model.response;

/**
 * Response body returned on successful token refresh ({@code HTTP 200}).
 *
 * @param accessToken new short-lived JWT access token (30 minutes)
 * @param expiresIn   access token lifetime in seconds (always 1800)
 */
public record RefreshResponse(String accessToken, int expiresIn) {
}
