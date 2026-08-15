package com.docsync.exception;

/**
 * Thrown when a registration attempt uses an email address that is already registered.
 * Maps to HTTP 409 Conflict in {@link GlobalExceptionHandler}.
 */
public class DuplicateEmailException extends RuntimeException {

    /**
     * Constructs a new {@code DuplicateEmailException} with the given message.
     *
     * @param message the detail message
     */
    public DuplicateEmailException(String message) {
        super(message);
    }
}
