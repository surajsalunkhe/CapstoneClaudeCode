package com.docsync.exception;

/**
 * Thrown when a presented token is invalid, malformed, or has been blacklisted (revoked).
 * Maps to HTTP 401 Unauthorized in {@link GlobalExceptionHandler}.
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * Constructs a new {@code InvalidTokenException} with the given message.
     *
     * @param message the detail message
     */
    public InvalidTokenException(String message) {
        super(message);
    }
}
