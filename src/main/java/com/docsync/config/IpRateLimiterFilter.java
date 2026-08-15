package com.docsync.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.resilience4j.ratelimiter.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces per-IP rate limiting on all authentication endpoints.
 * Uses a Caffeine {@link LoadingCache} keyed by client IP address, where each entry
 * holds a Resilience4j {@link RateLimiter} instance (RC-001).
 *
 * <p>When the rate limit is exceeded, the filter short-circuits the request with
 * HTTP 429 Too Many Requests and a standard {@code ApiResponse} JSON body.
 * No {@code @RateLimiter} annotations are used on controller methods.
 *
 * <p>Client IP resolution: always uses {@link HttpServletRequest#getRemoteAddr()}.
 * The {@code X-Forwarded-For} header is intentionally ignored because trusting it
 * without a verified proxy allowlist allows any client to forge their IP address and
 * bypass per-IP rate limiting. Deployments behind a trusted reverse proxy should
 * configure the proxy to rewrite the TCP remote address rather than rely on this header.
 */
@Component
public class IpRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(IpRateLimiterFilter.class);
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String RATE_LIMIT_RESPONSE =
        "{\"success\":false,\"message\":\"Too many requests. Please try again later.\","
            + "\"data\":null,\"errors\":null}";

    private final LoadingCache<String, RateLimiter> rateLimiters;

    /**
     * Constructs an {@code IpRateLimiterFilter} backed by a Caffeine loading cache.
     * Each IP address gets its own {@link RateLimiter} instance, created on demand via
     * {@link RateLimiterConfig#createRateLimiterForIp(String)}.
     * Cache entries expire 2 minutes after last access to release memory for idle IPs.
     *
     * @param config rate limiter configuration factory
     */
    public IpRateLimiterFilter(RateLimiterConfig config) {
        this.rateLimiters = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build(config::createRateLimiterForIp);
    }

    /**
     * Checks whether the client's request rate is within the allowed limit.
     * Passes through if permitted; returns HTTP 429 otherwise.
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
        String ip = resolveClientIp(request);
        RateLimiter limiter = rateLimiters.get(ip);
        if (limiter == null || !limiter.acquirePermission()) {
            LOG.warn("Rate limit exceeded for IP: {}", ip);
            response.setStatus(HTTP_TOO_MANY_REQUESTS);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(RATE_LIMIT_RESPONSE);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Resolves the client's IP address from the request using the TCP remote address.
     *
     * <p>The {@code X-Forwarded-For} header is intentionally ignored: without a validated
     * allowlist of trusted proxy CIDR ranges, any client can forge this header to supply a
     * different IP on every request, trivially circumventing per-IP rate limiting. Using
     * {@link HttpServletRequest#getRemoteAddr()} is the only safe default when no proxy
     * allowlist is configured.
     *
     * @param request the incoming HTTP request
     * @return resolved client IP address string
     */
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
