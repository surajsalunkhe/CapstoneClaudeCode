package com.docsync.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the per-IP Resilience4j {@link RateLimiter}.
 * Creates named rate limiters for each unique client IP address using the
 * {@code authRateLimiter} base configuration defined in {@code application.yml}.
 */
@Configuration
public class RateLimiterConfig {

    private final RateLimiterRegistry registry;

    /**
     * Constructs a {@code RateLimiterConfig} with the Resilience4j registry.
     *
     * @param registry Resilience4j rate limiter registry (auto-configured by Spring Boot)
     */
    public RateLimiterConfig(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Creates or retrieves a {@link RateLimiter} for the given client IP address.
     * The limiter uses the {@code authRateLimiter} configuration from
     * {@code application.yml} (5 requests per 60 seconds, zero timeout).
     *
     * @param ip the client IP address used as the limiter's unique name
     * @return a {@link RateLimiter} instance specific to this IP
     */
    public RateLimiter createRateLimiterForIp(String ip) {
        return registry.rateLimiter("authRateLimiter-" + ip,
            () -> registry.getConfiguration("authRateLimiter")
                .orElse(registry.getDefaultConfig()));
    }
}
