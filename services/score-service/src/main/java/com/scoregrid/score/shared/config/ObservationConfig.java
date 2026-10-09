package com.scoregrid.score.shared.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.observability.ContextProviderFactory;
import org.springframework.data.mongodb.observability.MongoHandlerContext;
import org.springframework.data.mongodb.observability.MongoObservationCommandListener;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * What reaches Tempo: the requests and events worth looking at, each with its
 * MongoDB commands as child spans, and none of the periodic plumbing.
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
     * One span per MongoDB command ("match_scores.find"), under the request or
     * event that sent it. Boot 4.1 registers neither part: the listener records
     * the span, and the context provider hands the synchronous driver the
     * current observation, which is how the span finds its parent. Spans carry
     * the command name, database and collection; never the query or the URI.
     *
     * <p>Spring Data MongoDB 5.1 deprecates these classes for removal in favour
     * of the driver's own ObservabilitySettings (driver 5.7+).
     */
    @Bean
    @SuppressWarnings("removal")
    MongoClientSettingsBuilderCustomizer mongoCommandObservations(ObservationRegistry registry) {
        return settings -> settings
                .contextProvider(ContextProviderFactory.create(registry))
                .addCommandListener(new MongoObservationCommandListener(registry));
    }

    /**
     * A command with no span open on its thread would be a one-span trace of
     * its own: index creation at startup, and the readiness check's hello every
     * 10 s. Checked on the span, not on getParentObservation(): inside the
     * untraced /actuator request the parent is that request's no-op
     * observation, not null.
     */
    @Bean
    @SuppressWarnings("removal")
    ObservationPredicate skipMongoCommandsOutsideATrace(Tracer tracer) {
        return (name, context) -> !(context instanceof MongoHandlerContext && tracer.currentSpan() == null);
    }
}
