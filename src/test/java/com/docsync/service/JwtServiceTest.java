package com.docsync.service;

import com.docsync.config.JwtProperties;
import com.docsync.model.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JwtService}.
 * No Spring context — uses a manually configured {@link JwtProperties}.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars-long!!";
    private static final long ACCESS_EXPIRY_MS = 1800000L;
    private static final long REFRESH_EXPIRY_MS = 604800000L;
    private static final long REMEMBER_ME_EXPIRY_MS = 2592000000L;

    private JwtService jwtService;
    private User testUser;

    /**
     * Sets up the JwtService with test properties before each test.
     */
    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setAccessTokenExpiryMs(ACCESS_EXPIRY_MS);
        props.setRefreshTokenExpiryMs(REFRESH_EXPIRY_MS);
        props.setRememberMeExpiryMs(REMEMBER_ME_EXPIRY_MS);
        jwtService = new JwtService(props);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("$2a$10$hashed");
    }

    /**
     * Verifies that a generated access token is a non-null, non-blank JWT string.
     */
    @Test
    void generateAccessToken_returnsValidJwt() {
        String token = jwtService.generateAccessToken(testUser);
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(token.contains("."));
    }

    /**
     * Verifies that a refresh token generated with rememberMe=false uses the standard expiry.
     */
    @Test
    void generateRefreshToken_usesDefaultExpiry() {
        String token = jwtService.generateRefreshToken(testUser);
        assertNotNull(token);
        assertFalse(jwtService.isTokenExpired(token));
    }

    /**
     * Verifies that a refresh token generated with rememberMe=true is not expired
     * (extended expiry is much further in the future).
     */
    @Test
    void generateRefreshToken_withRememberMe_usesExtendedExpiry() {
        String defaultToken = jwtService.generateRefreshToken(testUser, false);
        String rememberMeToken = jwtService.generateRefreshToken(testUser, true);
        assertFalse(jwtService.isTokenExpired(rememberMeToken));
        // Remember-me token should expire later than the default token
        assertTrue(jwtService.extractExpiration(rememberMeToken)
            .after(jwtService.extractExpiration(defaultToken)));
    }

    /**
     * Verifies that extractUsername returns the user's email from the token's sub claim.
     */
    @Test
    void extractUsername_returnsEmailFromSub() {
        String token = jwtService.generateAccessToken(testUser);
        String username = jwtService.extractUsername(token);
        assertEquals(testUser.getEmail(), username);
    }

    /**
     * Verifies that isTokenValid returns true for a freshly generated token.
     */
    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtService.generateAccessToken(testUser);
        assertTrue(jwtService.isTokenValid(token, testUser));
    }

    /**
     * Verifies that isTokenValid returns false when the token is for a different user.
     */
    @Test
    void isTokenValid_returnsFalseForWrongUser() {
        String token = jwtService.generateAccessToken(testUser);
        User otherUser = new User();
        otherUser.setEmail("other@example.com");
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    /**
     * Verifies that isTokenExpired returns false for a fresh token.
     */
    @Test
    void isTokenExpired_returnsFalseForFreshToken() {
        String token = jwtService.generateAccessToken(testUser);
        assertFalse(jwtService.isTokenExpired(token));
    }

    /**
     * Verifies that an expired token (1ms expiry) causes isTokenExpired to return true.
     */
    @Test
    void isTokenExpired_returnsTrueForExpiredToken() throws InterruptedException {
        JwtProperties shortExpiry = new JwtProperties();
        shortExpiry.setSecret(SECRET);
        shortExpiry.setAccessTokenExpiryMs(1L);
        shortExpiry.setRefreshTokenExpiryMs(1L);
        shortExpiry.setRememberMeExpiryMs(1L);
        JwtService shortLived = new JwtService(shortExpiry);
        String token = shortLived.generateAccessToken(testUser);
        Thread.sleep(5L);
        assertTrue(shortLived.isTokenExpired(token));
    }

    /**
     * Verifies that isTokenValid returns false for an expired token.
     */
    @Test
    void isTokenValid_returnsFalseForExpiredToken() throws InterruptedException {
        JwtProperties shortExpiry = new JwtProperties();
        shortExpiry.setSecret(SECRET);
        shortExpiry.setAccessTokenExpiryMs(1L);
        shortExpiry.setRefreshTokenExpiryMs(1L);
        shortExpiry.setRememberMeExpiryMs(1L);
        JwtService shortLived = new JwtService(shortExpiry);
        String token = shortLived.generateAccessToken(testUser);
        Thread.sleep(5L);
        assertFalse(shortLived.isTokenValid(token, testUser));
    }
}
