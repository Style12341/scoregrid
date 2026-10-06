package com.scoregrid.gateway.filter;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.HttpMethod;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.http.HttpConnectTimeoutException;

/**
 * {@code RetryReads} route filter: retry a GET whose connection to the
 * downstream could not be opened, 1 s apart. The request never left the
 * gateway, so a retry cannot double anything, and the lb() filter inside this
 * one picks the next replica: that is the failover. A read timeout, any
 * downstream response and any other method are passed through untouched.
 *
 * <p>Why not the stock {@code Retry} filter: in Gateway Server WebMvc 5.0.2 it
 * matches only the outermost exception, where a refused connect and a read
 * timeout are the same ResourceAccessException, and applies {@code methods}
 * only to status-based retries, so a POST that timed out is sent again.
 */
public final class ReadRetryFilterFunctions {

    private static final Logger log = LoggerFactory.getLogger(ReadRetryFilterFunctions.class);

    private ReadRetryFilterFunctions() {
    }

    /** {@code retries} is the number of attempts after the first one. */
    @Shortcut
    public static HandlerFilterFunction<ServerResponse, ServerResponse> retryReads(int retries) {
        RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .backOff(new FixedBackOff(1000, retries))
                .predicate(ReadRetryFilterFunctions::isConnectFailure)
                .build());
        retryTemplate.setRetryListener(new RetryListener() {
            @Override
            public void beforeRetry(RetryPolicy policy, Retryable<?> retryable, RetryState state) {
                log.warn("Retrying {} (retry {} of {}) after {}", retryable.getName(), state.getRetryCount(), retries,
                        NestedExceptionUtils.getMostSpecificCause(state.getLastException()).getClass().getSimpleName());
            }
        });
        return (request, next) -> HttpMethod.GET.equals(request.method())
                ? retryTemplate.execute(new Attempt(request, next))
                : next.handle(request);
    }

    /**
     * A failure to open the connection, anywhere in the cause chain. A plain
     * SocketTimeoutException is a read timeout: HttpClient 5 reports a
     * connect timeout as its own ConnectTimeoutException subclass of it.
     */
    static boolean isConnectFailure(@Nullable Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof NoRouteToHostException
                    || t instanceof ConnectTimeoutException || t instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record Attempt(ServerRequest request, HandlerFunction<ServerResponse> next)
            implements Retryable<ServerResponse> {

        @Override
        public ServerResponse execute() throws Exception {
            return next.handle(request);
        }

        @Override
        public String getName() {
            return request.method() + " " + request.path() + " on route "
                    + request.attributes().get(MvcUtils.GATEWAY_ROUTE_ID_ATTR);
        }
    }

    /** Registered as a bean so route YAML can name the filter {@code RetryReads}. */
    public static class FilterSupplier extends SimpleFilterSupplier {

        public FilterSupplier() {
            super(ReadRetryFilterFunctions.class);
        }
    }
}
