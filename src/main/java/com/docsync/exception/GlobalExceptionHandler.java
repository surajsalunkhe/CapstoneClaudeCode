package com.docsync.exception;

import com.docsync.model.response.ApiResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that maps all application and framework exceptions to
 * the standard {@link ApiResponse} envelope with the appropriate HTTP status code.
 * One handler method per exception type — no exceptions are caught and swallowed silently.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles duplicate email registration attempts.
     *
     * @param e the exception
     * @return HTTP 409 response with error message
     */
    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicate(DuplicateEmailException e) {
        LOG.warn("Duplicate email registration attempt: {}", e.getMessage());
        return ApiResponse.failure(e.getMessage(), null);
    }

    /**
     * Handles invalid login credentials.
     *
     * @param e the exception
     * @return HTTP 401 response with generic error message
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException e) {
        LOG.warn("Invalid credentials attempt");
        return ApiResponse.failure(e.getMessage(), null);
    }

    /**
     * Handles locked account login attempts.
     *
     * @param e the exception
     * @return HTTP 403 response with lockout message
     */
    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleLocked(AccountLockedException e) {
        LOG.warn("Login attempt on locked account");
        return ApiResponse.failure(e.getMessage(), null);
    }

    /**
     * Handles invalid or revoked tokens and expired tokens.
     *
     * @param e the exception (either {@link InvalidTokenException} or
     *          {@link TokenExpiredException})
     * @return HTTP 401 response with token error message
     */
    @ExceptionHandler({InvalidTokenException.class, TokenExpiredException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidToken(RuntimeException e) {
        LOG.warn("Token validation failure: {}", e.getMessage());
        return ApiResponse.failure(e.getMessage(), null);
    }

    /**
     * Handles Jakarta Bean Validation failures on request bodies.
     * Extracts field-level error messages and returns them in the {@code errors} array.
     *
     * @param e the validation exception containing field errors
     * @return HTTP 400 response with list of field-level validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
        return ApiResponse.failure("Validation failed", errors);
    }

    /**
     * Handles Resilience4j rate limit exceeded events.
     *
     * @param e the rate limit exception
     * @return HTTP 429 response with rate limit message
     */
    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleRateLimit(RequestNotPermitted e) {
        LOG.warn("Rate limit exceeded: {}", e.getMessage());
        return ApiResponse.failure("Too many requests. Please try again later.", null);
    }

    /**
     * Handles Spring Security disabled account exceptions.
     *
     * @param e the exception
     * @return HTTP 403 response (FINDING-010)
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleDisabled(DisabledException e) {
        LOG.warn("Login attempt on disabled account");
        return ApiResponse.failure("Account is disabled", null);
    }

    /**
     * Handles database unavailability and other Spring Data access exceptions.
     *
     * @param e the data access exception
     * @return HTTP 503 response without exposing internal details
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleDataAccess(DataAccessException e) {
        LOG.error("Database error occurred", e);
        return ApiResponse.failure("Service temporarily unavailable", null);
    }

    /**
     * Handles illegal argument exceptions from service-layer business rule validation.
     *
     * @param e the exception
     * @return HTTP 400 response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.failure(e.getMessage(), null);
    }

    /**
     * Catch-all handler for any unhandled exceptions.
     * Logs the full stack trace at ERROR level but returns a generic message to the client
     * to avoid exposing internal details.
     *
     * @param e the unexpected exception
     * @return HTTP 500 response with generic error message
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        LOG.error("Unexpected error occurred", e);
        return ApiResponse.failure("An unexpected error occurred", null);
    }
}
