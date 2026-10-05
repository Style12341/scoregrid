package com.scoregrid.tournament.shared.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers for this service's outbound calls. Nothing calls one yet:
 * {@code resultsProvider} is reserved for the external results provider,
 * which is off by default.
 *
 * <p>Read AGENTS.md §5 before adding resilience config anywhere else. In
 * short: configure breakers here, in Java, and call them through
 * {@code CircuitBreakerFactory}; {@code @CircuitBreaker} / {@code @Retry}
 * compile but nothing applies them. A {@code timeLimiterConfig(...)} in these
 * customizers is ignored: Spring Cloud CircuitBreaker 5.0.2 takes the time
 * limiter only from the TimeLimiterRegistry, so every breaker here runs on
 * Resilience4J's 1 s default until this class defines a
 * {@code TimeLimiterRegistry} bean the way prediction-service's
 * ResilienceConfig does. Add it together with the first real caller, sized
 * from that caller's HTTP client timeouts.
 */
@Configuration
public class ResilienceConfig {

    /** Named instance for this service's outbound dependency: resultsProvider */
    public static final String RESULTS_PROVIDER = "resultsProvider";

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .build())
                .build());
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> resultsProviderCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build()), RESULTS_PROVIDER);
    }
}
