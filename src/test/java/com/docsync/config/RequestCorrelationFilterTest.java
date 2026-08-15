package com.docsync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Unit tests for {@link RequestCorrelationFilter}.
 */
@ExtendWith(MockitoExtension.class)
class RequestCorrelationFilterTest {

    @Mock
    private FilterChain filterChain;

    private RequestCorrelationFilter filter;

    /**
     * Initialises the filter before each test.
     */
    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
    }

    /**
     * Verifies that an incoming X-Request-Id header value is placed in the MDC.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void populatesMdcWithRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "test-request-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doAnswer(inv -> {
            assertEquals("test-request-id-123", MDC.get("requestId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("test-request-id-123", response.getHeader("X-Request-Id"));
    }

    /**
     * Verifies that a UUID is generated as the request ID when no header is present.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void generatesRequestIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        doAnswer(inv -> {
            assertNotNull(MDC.get("requestId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(response.getHeader("X-Request-Id"));
    }

    /**
     * Verifies that the MDC is cleared after the request completes.
     *
     * @throws Exception if the filter throws
     */
    @Test
    void clearesMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get("requestId"));
    }
}
