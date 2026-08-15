package com.docsync.model.response;

import java.util.List;

/**
 * Standard API response envelope used by all endpoints.
 * Every response — success or error — is wrapped in this record to provide
 * a consistent structure for API consumers (NFR-004.2).
 *
 * @param <T>     the type of the {@code data} payload
 * @param success {@code true} if the request succeeded, {@code false} on error
 * @param message human-readable status or error message
 * @param data    the response payload; {@code null} on error responses
 * @param errors  list of field-level error messages; {@code null} on success responses
 */
public record ApiResponse<T>(boolean success, String message, T data, List<String> errors) {

    /**
     * Canonical constructor that defensively copies the {@code errors} list to prevent
     * external mutation of the record's internal state (SpotBugs EI_EXPOSE_REP2).
     *
     * @param success {@code true} if the request succeeded
     * @param message human-readable status or error message
     * @param data    the response payload
     * @param errors  list of field-level error messages; may be {@code null}
     */
    public ApiResponse {
        errors = errors != null ? List.copyOf(errors) : null;
    }

    /**
     * Creates a successful {@code ApiResponse} with the given message and data payload.
     *
     * @param <T>     the data payload type
     * @param message success message
     * @param data    response payload
     * @return a success response with {@code success=true} and {@code errors=null}
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    /**
     * Creates a failure {@code ApiResponse} with the given message and error list.
     *
     * @param <T>     the data payload type (typically {@code Void})
     * @param message error summary message
     * @param errors  list of specific error descriptions; may be {@code null}
     * @return a failure response with {@code success=false} and {@code data=null}
     */
    public static <T> ApiResponse<T> failure(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
