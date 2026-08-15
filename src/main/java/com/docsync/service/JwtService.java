package com.docsync.service;

import com.docsync.config.JwtProperties;
import com.docsync.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Service for generating, signing, parsing, and validating JSON Web Tokens.
 * Uses HMAC-SHA256 (HS256) signing with a key derived from {@link JwtProperties#getSecret()}.
 * No database calls are made in this class.
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    /**
     * Constructs a {@code JwtService} with the provided JWT configuration properties.
     *
     * @param jwtProperties configuration bean containing secret and expiry settings
     */
    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Generates a short-lived access token for the given user.
     * The token contains {@code sub} (email), {@code userId}, {@code roles}, {@code iat},
     * and {@code exp} claims (ADR-007).
     *
     * @param user the authenticated user
     * @return signed JWT access token string
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiryMs());
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId() != null ? user.getId().toString() : null)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey())
            .compact();
    }

    /**
     * Generates a refresh token for the given user using the default refresh token expiry.
     *
     * @param user the authenticated user
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(User user) {
        return generateRefreshToken(user, false);
    }

    /**
     * Generates a refresh token for the given user.
     * When {@code rememberMe} is {@code true}, the token lifetime is extended to
     * {@code jwt.remember-me-expiry-ms} (30 days by default); otherwise the standard
     * {@code jwt.refresh-token-expiry-ms} (7 days by default) is used (ADR-001).
     *
     * @param user       the authenticated user
     * @param rememberMe whether to use the extended "remember me" expiry
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(User user, boolean rememberMe) {
        long expiryMs = rememberMe
            ? jwtProperties.getRememberMeExpiryMs()
            : jwtProperties.getRefreshTokenExpiryMs();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId() != null ? user.getId().toString() : null)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey())
            .compact();
    }

    /**
     * Extracts the subject (email) from the given token.
     *
     * @param token the JWT string
     * @return the email address embedded in the {@code sub} claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from the given token.
     *
     * @param token the JWT string
     * @return the expiry {@link Date}
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Returns whether the given token is valid for the specified user.
     * A token is valid when: its subject matches the user's username AND it has not expired.
     *
     * @param token       the JWT string
     * @param userDetails the user against whom to validate
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns whether the given token has passed its expiry time.
     *
     * @param token the JWT string
     * @return {@code true} if the token has expired, {@code false} if it is still valid
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
