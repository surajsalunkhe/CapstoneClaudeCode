package com.docsync.model.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the {@code POST /api/v1/auth/register} endpoint.
 * All fields are validated via Jakarta Bean Validation before the service is invoked.
 *
 * @param email           the registrant's email address (must be valid RFC-5322 format)
 * @param password        the desired password (must meet complexity requirements)
 * @param confirmPassword must match {@code password} exactly
 * @param acceptTerms     must be {@code true}; a {@code false} value returns HTTP 400
 */
public record RegisterRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    String email,

    @NotBlank(message = "Password must not be blank")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "Password must be at least 8 characters and contain uppercase, "
            + "lowercase, digit, and special character"
    )
    String password,

    @NotBlank(message = "Confirm password must not be blank")
    String confirmPassword,

    @AssertTrue(message = "You must accept terms and conditions")
    boolean acceptTerms
) {
}
