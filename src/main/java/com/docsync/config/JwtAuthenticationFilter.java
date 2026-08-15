package com.docsync.config;

import com.docsync.service.JwtService;
import com.docsync.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that validates JWT Bearer tokens on every request.
 * Extracts the token from the {@code Authorization} header, checks the blacklist,
 * validates the signature and expiry, and populates the {@link SecurityContextHolder}.
 *
 * <p>Requests without an {@code Authorization: Bearer} header are passed through
 * without modification, allowing public endpoints to function without authentication.
 *
 * <p>This filter contains no business logic — it delegates to {@link JwtService}
 * and {@link TokenBlacklistService} for all validation decisions.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final String UNAUTHORIZED_RESPONSE =
        "{\"success\":false,\"message\":\"Invalid or revoked token\",\"data\":null,\"errors\":null}";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Constructs a {@code JwtAuthenticationFilter} with the required dependencies.
     *
     * @param jwtService            service for JWT parsing and validation
     * @param userDetailsService    service for loading user details by username
     * @param tokenBlacklistService service for checking token revocation status
     */
    public JwtAuthenticationFilter(JwtService jwtService,
            UserDetailsService userDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Validates the JWT Bearer token and sets the Spring Security context if valid.
     * Returns HTTP 401 if the token is blacklisted.
     * Passes through if no {@code Authorization} header is present.
     *
     * @param request  incoming HTTP request
     * @param response outgoing HTTP response
     * @param chain    remainder of the filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX_LENGTH);

        if (tokenBlacklistService.isBlacklisted(token)) {
            LOG.warn("Rejected blacklisted token attempt");
            sendUnauthorized(response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            LOG.warn("JWT validation failed: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HTTP_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(UNAUTHORIZED_RESPONSE);
    }
}
