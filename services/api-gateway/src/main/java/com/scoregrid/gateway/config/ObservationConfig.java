package com.scoregrid.gateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps the gateway's periodic plumbing traffic out of the traces. Observed,
 * each call is a trace of its own in Tempo every few seconds, burying the
 * requests worth looking at.
 */
@Configuration
public class ObservationConfig {

    /**
     * Prometheus scrapes and Docker health checks. They also leave
     * http_server_requests, which the dashboard already filtered them out of.
     */
    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    /**
     * The Eureka heartbeat and registry fetch, every 5 s. Only the gateway
     * traces them: its Eureka client uses Boot's observed RestClient.Builder.
     */
    @Bean
    ObservationPredicate skipEurekaClientObservations() {
        return (name, context) -> !(context instanceof ClientRequestObservationContext client
                && client.getCarrier() != null
                && client.getCarrier().getURI().getPath().startsWith("/eureka/"));
    }
}
