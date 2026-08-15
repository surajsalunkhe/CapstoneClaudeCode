package com.docsync.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link IpRateLimiterFilter}.
 */
@ExtendWith(MockitoExtension.class)
class IpRateLimiterFilterTest {

    @Mock
    private FilterChain filterChain;

    private IpRateLimiterFilter filter;
    private com.docsync.config.RateLimiterConfig rateLimiterConfig;

    /**
     * Sets up the filter with a real rate limiter registry (1 req per 60s for testing).
     */
    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofSeconds(60))
            .timeoutDuration(Duration.ZERO)
            .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        rateLimiterConfig = new com.docsync.config.RateLimiterConfig(registry);
        filter = new IpRateLimiterFilter(rateLimiterConfig);
    }

    /**
     * Verifies that a request within the rate limit is allowed through.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void filter_allowsRequestUnderLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    /**
     * Verifies that a request exceeding the rate limit returns HTTP 429.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void filter_returns429WhenLimitExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");

        // Exhaust the 5-request limit
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // 6th request should be rate-limited
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, overLimitResponse, filterChain);

        assertEquals(429, overLimitResponse.getStatus());
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    /**
     * Verifies that X-Forwarded-For is ignored and RemoteAddr is always used for IP resolution
     * (CR-003: XFF not trusted without a proxy allowlist).
     * Two requests with different XFF values but the same RemoteAddr share a rate limit bucket.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void filter_ignoresXForwardedForAndUsesRemoteAddr() throws Exception {
        // Two requests from the same RemoteAddr but different XFF values.
        // If XFF were trusted they would consume separate buckets; using RemoteAddr they share one.
        MockHttpServletRequest requestA = new MockHttpServletRequest();
        requestA.setRemoteAddr("10.0.0.3");
        requestA.addHeader("X-Forwarded-For", "1.2.3.4");

        MockHttpServletRequest requestB = new MockHttpServletRequest();
        requestB.setRemoteAddr("10.0.0.3");
        requestB.addHeader("X-Forwarded-For", "5.6.7.8");

        // Exhaust the 5-request limit using alternating XFF values but same RemoteAddr
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockHttpServletRequest req = (i % 2 == 0) ? requestA : requestB;
            filter.doFilterInternal(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }

        // 6th request from same RemoteAddr must be rate-limited, proving XFF is ignored
        MockHttpServletResponse overLimit = new MockHttpServletResponse();
        filter.doFilterInternal(requestA, overLimit, filterChain);
        assertEquals(429, overLimit.getStatus());
    }

    /**
     * Verifies that RemoteAddr is always used for IP resolution even without XFF header.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void filter_usesRemoteAddrForRateLimiting() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(any(), any());
    }
}
