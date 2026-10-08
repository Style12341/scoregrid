package com.scoregrid.tournament.shared.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Prometheus scrapes and Docker health checks hit /actuator every 10-15 s.
 * Observed, each one is a trace of its own in Tempo, burying the requests
 * worth looking at, and a sample in http_server_requests, which the dashboard
 * already filtered them out of.
 */
@Configuration
public class ObservationConfig {

    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}
