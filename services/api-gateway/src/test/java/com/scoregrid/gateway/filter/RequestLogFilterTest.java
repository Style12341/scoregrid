package com.scoregrid.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The status in the request log line is the one the client gets, and
 * actuator calls are not logged.
 */
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> logLines = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logLines.start();
        logger.addAppender(logLines);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(logLines);
    }

    @Test
    void successfulRequest_isLoggedWithItsStatus() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/tournaments"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(loggedLines()).singleElement().asString()
                .startsWith("Request handled: method=GET path=/api/tournaments status=200 durationMs=");
    }

    @Test
    void downstreamStatus_isLoggedAsIs() throws Exception {
        FilterChain downstreamUnavailable = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(503);

        filter.doFilter(new MockHttpServletRequest("PUT", "/api/matches/7/result"), new MockHttpServletResponse(),
                downstreamUnavailable);

        assertThat(loggedLines()).singleElement().asString().contains("method=PUT path=/api/matches/7/result status=503");
    }

    @Test
    void exceptionFromTheChain_isLoggedAs500AndRethrown() {
        FilterChain failing = (request, response) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/rankings/global"),
                new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(loggedLines()).singleElement().asString().contains("path=/api/rankings/global status=500");
    }

    @Test
    void actuatorRequests_areNotLogged() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/readiness"), new MockHttpServletResponse(),
                chain);

        assertThat(chain.getRequest()).as("the request still goes through").isNotNull();
        assertThat(loggedLines()).isEmpty();
    }

    private List<String> loggedLines() {
        return logLines.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
