package com.docsync.model.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body returned on successful user registration ({@code HTTP 201}).
 *
 * @param userId    the new user's UUID primary key
 * @param email     the registered email address
 * @param createdAt the timestamp when the account was created
 */
public record RegisterResponse(UUID userId, String email, Instant createdAt) {
}
