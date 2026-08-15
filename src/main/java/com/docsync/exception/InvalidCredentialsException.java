package com.docsync.exception;

/**
 * Thrown when login credentials are incorrect (wrong password or unknown email).
 * Maps to HTTP 401 Unauthorized in {@link GlobalExceptionHandler}.
 * The message is always the generic "Invalid email or password" to prevent user enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Constructs a new {@code InvalidCredentialsException} with the given message.
     *
     * @param message the detail message (always generic to prevent enumeration)
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
