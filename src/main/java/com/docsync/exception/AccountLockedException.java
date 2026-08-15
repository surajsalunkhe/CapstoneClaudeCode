package com.docsync.exception;

/**
 * Thrown when a login attempt is made against an account that is temporarily locked
 * due to excessive failed login attempts.
 * Maps to HTTP 403 Forbidden in {@link GlobalExceptionHandler}.
 */
public class AccountLockedException extends RuntimeException {

    /**
     * Constructs a new {@code AccountLockedException} with the given message.
     *
     * @param message the detail message
     */
    public AccountLockedException(String message) {
        super(message);
    }
}
