package com.scoregrid.prediction.shared.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient builders.
 *
 * <p>The {@code @Primary} plain builder exists for framework clients: Eureka's
 * own transport takes a RestClient.Builder and sets its own request factory.
 * Calls to other ScoreGrid services use the {@code @LoadBalanced} one, which
 * resolves {@code http://<service-name>} through the LoadBalancer on every
 * request.
 *
 * <p>The timeouts are set here, on an explicit request factory, because Boot's
 * auto-configured RestClient.Builder (spring-boot-restclient, the module that
 * applies {@code spring.http.clients.*}) is not on this service's classpath.
 * Without them, {@code RestClient.builder()} picks Apache HttpClient 5 (present
 * through the Eureka client), whose default connect timeout is three minutes
 * and which has no response timeout: a call to a killed replica's address
 * hangs until the breaker's time limiter gives up, and the retry never gets to
 * the live replica.
 *
 * <p>The ObservationRegistry is set by hand for the same reason: a builder from
 * {@code RestClient.builder()} records no observation, so the call would get no
 * client span and send no trace headers, and the trace would stop here.
 */
@Configuration(proxyBeanMethods = false)
public class ClientConfig {

    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder loadBalancedRestClientBuilder(JdkClientHttpRequestFactory serviceRequestFactory,
                                                     ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .requestFactory(serviceRequestFactory)
                .observationRegistry(observationRegistry);
    }

    /**
     * The JDK client rather than HttpClient 5: its connect timeout is a builder
     * setting instead of a connection-manager one, and its pool has no
     * five-connections-per-host cap for concurrent requests to queue behind.
     * HTTP/1.1 pinned so plain-http requests do not carry an h2c upgrade.
     */
    @Bean
    JdkClientHttpRequestFactory serviceRequestFactory(
            @Value("${scoregrid.clients.connect-timeout}") Duration connectTimeout,
            @Value("${scoregrid.clients.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
