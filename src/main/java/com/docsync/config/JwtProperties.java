package com.docsync.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds JWT configuration properties from {@code application.yml} (prefix {@code jwt}).
 * Performs startup validation to ensure the signing secret meets minimum length requirements.
 */
@ConfigurationProperties(prefix = "jwt")
@Validated
public class JwtProperties {

    @NotBlank
    private String secret;

    private long accessTokenExpiryMs;

    private long refreshTokenExpiryMs;

    private long rememberMeExpiryMs;

    /**
     * Validates that the JWT secret is at least 32 characters (256 bits) at startup.
     * Throws {@link IllegalStateException} if the secret is too short, preventing the
     * application from starting with an insecure signing key.
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "jwt.secret must be at least 32 characters (256 bits). "
                    + "Current length: " + (secret == null ? 0 : secret.length()));
        }
    }

    /**
     * Returns the JWT signing secret.
     *
     * @return secret string (minimum 32 characters)
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the JWT signing secret.
     *
     * @param secret signing secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Returns the access token lifetime in milliseconds.
     *
     * @return expiry in milliseconds
     */
    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    /**
     * Sets the access token lifetime in milliseconds.
     *
     * @param accessTokenExpiryMs expiry in milliseconds
     */
    public void setAccessTokenExpiryMs(long accessTokenExpiryMs) {
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Returns the refresh token lifetime in milliseconds.
     *
     * @return expiry in milliseconds
     */
    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    /**
     * Sets the refresh token lifetime in milliseconds.
     *
     * @param refreshTokenExpiryMs expiry in milliseconds
     */
    public void setRefreshTokenExpiryMs(long refreshTokenExpiryMs) {
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Returns the "remember me" extended refresh token lifetime in milliseconds.
     *
     * @return expiry in milliseconds
     */
    public long getRememberMeExpiryMs() {
        return rememberMeExpiryMs;
    }

    /**
     * Sets the "remember me" extended refresh token lifetime in milliseconds.
     *
     * @param rememberMeExpiryMs expiry in milliseconds
     */
    public void setRememberMeExpiryMs(long rememberMeExpiryMs) {
        this.rememberMeExpiryMs = rememberMeExpiryMs;
    }
}
