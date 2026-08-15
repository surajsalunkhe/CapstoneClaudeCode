package com.docsync.service;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TokenBlacklistService}.
 */
class TokenBlacklistServiceTest {

    private TokenBlacklistService tokenBlacklistService;

    /**
     * Initialises a fresh {@link TokenBlacklistService} before each test.
     */
    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService();
    }

    /**
     * Verifies that a blacklisted token is recognised as blacklisted.
     */
    @Test
    void blacklist_addsTokenToCache() {
        Date expiry = new Date(System.currentTimeMillis() + 1800000L);
        tokenBlacklistService.blacklist("test-token", expiry);
        assertTrue(tokenBlacklistService.isBlacklisted("test-token"));
    }

    /**
     * Verifies that isBlacklisted returns true for a blacklisted token.
     */
    @Test
    void isBlacklisted_returnsTrueForBlacklistedToken() {
        String token = "some.jwt.token";
        Date expiry = new Date(System.currentTimeMillis() + 60000L);
        tokenBlacklistService.blacklist(token, expiry);
        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }

    /**
     * Verifies that isBlacklisted returns false for an unknown token.
     */
    @Test
    void isBlacklisted_returnsFalseForUnknownToken() {
        assertFalse(tokenBlacklistService.isBlacklisted("not-blacklisted-token"));
    }
}
