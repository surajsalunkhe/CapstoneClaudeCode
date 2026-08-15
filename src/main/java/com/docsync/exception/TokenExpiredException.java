package com.docsync.exception;

/**
 * Thrown when an access or refresh token has passed its expiry time.
 * Maps to HTTP 401 Unauthorized in {@link GlobalExceptionHandler}.
 */
public class TokenExpiredException extends RuntimeException {

    /**
     * Constructs a new {@code TokenExpiredException} with the given message.
     *
     * @param message the detail message
     */
    public TokenExpiredException(String message) {
        super(message);
    }
}
