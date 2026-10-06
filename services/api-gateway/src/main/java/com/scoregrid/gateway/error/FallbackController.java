package com.scoregrid.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Target of every route's CircuitBreaker {@code fallbackUri}.
 *
 * <p>Reached by a servlet forward whenever the proxied call fails: connection
 * refused, connect or read timeout, no registered instance, or the breaker
 * already open. The client gets 503 {@code DOWNSTREAM_UNAVAILABLE} in the
 * contract envelope instead of a hang or a bare 500.
 *
 * <p>No method restriction on purpose: the forward keeps the original method,
 * so a failed POST arrives here as a POST.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private final ErrorResponseWriter errorResponseWriter;

    public FallbackController(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @RequestMapping("/fallback/{service:[a-z-]+}")
    public void downstreamUnavailable(@PathVariable String service,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        Object cause = request.getAttribute(MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        // One line, no stack trace: during an outage this fires on every request.
        log.warn("Downstream {} unavailable for {} {}: {}", service, request.getMethod(),
                ErrorResponseWriter.originalPath(request), describe(cause));
        errorResponseWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                service + " is unavailable. Please try again later.");
    }

    /**
     * The innermost cause: "RetryException" says less than "Unable to find
     * instance" or "Connect timed out" when reading the logs during an outage.
     */
    private static String describe(Object cause) {
        if (!(cause instanceof Throwable throwable)) {
            return "unknown cause";
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(throwable);
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
