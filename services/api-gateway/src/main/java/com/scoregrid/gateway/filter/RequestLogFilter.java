package com.scoregrid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * One INFO line per request through the gateway: method, path, status and
 * duration. Actuator calls (Prometheus scrapes, Docker health checks) are
 * skipped. Only the path is logged: no query string, header or body, so no
 * token or password can reach the log.
 *
 * <p>Why a filter: Tomcat's access log is plain text, so it would lose the
 * JSON fields Promtail reads, and it has no traceId.
 */
@Component
@Order(RequestLogFilter.ORDER)
public class RequestLogFilter extends OncePerRequestFilter {

    /**
     * Right inside Boot's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1),
     * so the request's traceId is in the MDC when the line is written, and
     * before Spring Security (-100), so a request refused at the edge with 401
     * is logged too.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        // An exception escaping the chain becomes a 500 later, but the
        // response still says 200 at this point: log what the client gets.
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            log.info("Request handled: method={} path={} status={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }
}
