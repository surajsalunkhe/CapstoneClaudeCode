package com.docsync.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link JwtProperties} startup validation logic.
 */
class JwtPropertiesTest {

    /**
     * Validates that a secret shorter than 32 characters throws {@link IllegalStateException}.
     */
    @Test
    void validateSecret_throwsOnShortSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret("tooshort");
        assertThrows(IllegalStateException.class, props::validateSecret);
    }

    /**
     * Validates that a null secret throws {@link IllegalStateException}.
     */
    @Test
    void validateSecret_throwsOnNullSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret(null);
        assertThrows(IllegalStateException.class, props::validateSecret);
    }

    /**
     * Validates that a secret of exactly 32 characters passes validation without exception.
     */
    @Test
    void validateSecret_passesOnValidSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret("a-valid-secret-that-is-32-chars!!");
        assertDoesNotThrow(props::validateSecret);
    }

    /**
     * Validates that a secret longer than 32 characters passes validation.
     */
    @Test
    void validateSecret_passesOnLongerSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret("this-is-a-very-long-secret-that-exceeds-32-characters-easily");
        assertDoesNotThrow(props::validateSecret);
    }
}
