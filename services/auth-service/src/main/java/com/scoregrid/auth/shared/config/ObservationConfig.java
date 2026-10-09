package com.scoregrid.auth.shared.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import net.ttddyy.observation.tracing.QueryContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * What reaches Tempo: the requests and events worth looking at, each with its
 * JDBC queries as child spans (jdbc.* in application.yml), and none of the
 * periodic plumbing.
 */
@Configuration
public class ObservationConfig {

    /**
     * Prometheus scrapes and Docker health checks hit /actuator every 10-15 s.
     * Observed, each one is a trace of its own in Tempo, burying the requests
     * worth looking at, and a sample in http_server_requests, which the
     * dashboard already filtered them out of.
     */
    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    /**
     * A query with no span open on its thread would be a one-span trace of its
     * own: Flyway's and the other startup queries, a few dozen at every start.
     * Checked on the span, not on getParentObservation(), which is not null
     * for them.
     */
    @Bean
    ObservationPredicate skipQueriesOutsideATrace(Tracer tracer) {
        return (name, context) -> !(context instanceof QueryContext && tracer.currentSpan() == null);
    }
}
