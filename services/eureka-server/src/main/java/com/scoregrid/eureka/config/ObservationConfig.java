package com.scoregrid.eureka.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Prometheus scrapes and Docker health checks hit /actuator every 10-15 s.
 * Eureka exports no spans, but each call would still be a sample in
 * http_server_requests, which the dashboard already filtered them out of. Same
 * predicate as the other services, so none of them observes actuator.
 */
@Configuration
public class ObservationConfig {

    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}
