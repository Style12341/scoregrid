package com.scoregrid.gateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator calls and the Eureka client are not observed; requests through the
 * gateway and its calls to the services still are.
 */
class ObservationConfigTest {

    private final ObservationConfig config = new ObservationConfig();

    @Test
    void skipsActuatorButObservesApiRequests() {
        ObservationPredicate predicate = config.skipActuatorObservations();

        assertThat(predicate.test("http.server.requests", serverRequest("/actuator/prometheus"))).isFalse();
        assertThat(predicate.test("http.server.requests", serverRequest("/actuator/health/readiness"))).isFalse();
        assertThat(predicate.test("http.server.requests", serverRequest("/api/tournaments"))).isTrue();
    }

    @Test
    void skipsEurekaButObservesCallsToServices() {
        ObservationPredicate predicate = config.skipEurekaClientObservations();

        assertThat(predicate.test("http.client.requests",
                clientRequest(HttpMethod.PUT, "http://eureka-server:8761/eureka/apps/API-GATEWAY/gw:8080"))).isFalse();
        assertThat(predicate.test("http.client.requests",
                clientRequest(HttpMethod.GET, "http://eureka-server:8761/eureka/apps/delta"))).isFalse();
        assertThat(predicate.test("http.client.requests",
                clientRequest(HttpMethod.GET, "http://172.20.0.9:8082/api/tournaments"))).isTrue();
    }

    private static ServerRequestObservationContext serverRequest(String path) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", path),
                new MockHttpServletResponse());
    }

    private static ClientRequestObservationContext clientRequest(HttpMethod method, String uri) {
        return new ClientRequestObservationContext(new MockClientHttpRequest(method, URI.create(uri)));
    }
}
