package com.docsync.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LoginAttemptService}.
 * No Spring context — uses a {@link SimpleMeterRegistry} for metrics.
 */
class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;
    private static final String EMAIL = "user@example.com";

    /**
     * Initialises a fresh {@link LoginAttemptService} before each test.
     */
    @BeforeEach
    void setUp() {
        MeterRegistry registry = new SimpleMeterRegistry();
        loginAttemptService = new LoginAttemptService(registry);
    }

    /**
     * Verifies that one failure is recorded without locking the account.
     */
    @Test
    void recordFailure_incrementsCounter() {
        loginAttemptService.recordFailure(EMAIL);
        assertFalse(loginAttemptService.isLocked(EMAIL));
    }

    /**
     * Verifies that fewer than 5 failures do not lock the account.
     */
    @Test
    void isLocked_returnsFalseBeforeFiveFailures() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        assertFalse(loginAttemptService.isLocked(EMAIL));
    }

    /**
     * Verifies that exactly 5 failures lock the account.
     */
    @Test
    void isLocked_returnsTrueAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }
        assertTrue(loginAttemptService.isLocked(EMAIL));
    }

    /**
     * Verifies that resetAttempts removes the failure counter.
     */
    @Test
    void resetAttempts_clearsCounter() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.resetAttempts(EMAIL);
        assertFalse(loginAttemptService.isLocked(EMAIL));
    }

    /**
     * Verifies that isLocked returns false after a counter reset even if 5 failures occurred.
     */
    @Test
    void isLocked_returnsFalseAfterReset() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }
        assertTrue(loginAttemptService.isLocked(EMAIL));
        loginAttemptService.resetAttempts(EMAIL);
        assertFalse(loginAttemptService.isLocked(EMAIL));
    }
}
