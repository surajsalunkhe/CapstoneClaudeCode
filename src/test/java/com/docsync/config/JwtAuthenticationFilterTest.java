package com.docsync.config;

import com.docsync.model.entity.User;
import com.docsync.service.JwtService;
import com.docsync.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} (RC-008 — FINDING-020).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private User testUser;

    /**
     * Sets up the filter and clears the security context before each test.
     */
    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, tokenBlacklistService);
        SecurityContextHolder.clearContext();

        testUser = new User();
        testUser.setEmail("user@example.com");
        testUser.setPasswordHash("hash");
        testUser.setEnabled(true);
    }

    /**
     * Verifies that a valid token results in the SecurityContext being populated.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void validToken_setsSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenBlacklistService.isBlacklisted("valid.token")).thenReturn(false);
        when(jwtService.extractUsername("valid.token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(testUser);
        when(jwtService.isTokenValid("valid.token", testUser)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    /**
     * Verifies that a blacklisted token returns HTTP 401 without calling the filter chain.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void blacklistedToken_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blacklisted.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenBlacklistService.isBlacklisted("blacklisted.token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    /**
     * Verifies that a request without Authorization header passes through unchanged.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void missingAuthHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
    }

    /**
     * Verifies that a malformed Bearer header (without "Bearer " prefix) passes through.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void invalidBearerFormat_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
    }

    /**
     * Verifies that an expired (invalid) token does not populate the SecurityContext,
     * but still passes through to allow public endpoints to handle the request.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void expiredToken_doesNotSetSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenBlacklistService.isBlacklisted("expired.token")).thenReturn(false);
        when(jwtService.extractUsername("expired.token"))
            .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }
}
