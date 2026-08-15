package com.docsync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates the SLF4J MDC with a per-request correlation ID.
 * Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the {@code requestId} is available
 * in MDC before any other filter or handler logs a message (FINDING-008).
 *
 * <p>If the incoming request contains an {@code X-Request-Id} header, its value is used
 * as the correlation ID. Otherwise a new UUID is generated. The ID is echoed back to the
 * caller in the {@code X-Request-Id} response header for end-to-end tracing.
 *
 * <p>The MDC entry is always removed in a {@code finally} block to prevent leakage
 * between pooled threads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    /**
     * Populates the MDC with the request correlation ID, processes the request,
     * then clears the MDC entry.
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
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
