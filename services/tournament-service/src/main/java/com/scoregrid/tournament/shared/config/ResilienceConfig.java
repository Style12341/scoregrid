package com.scoregrid.tournament.shared.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers for this service's outbound calls.
 *
 * <p>IMPORTANT — read before adding resilience config anywhere else:
 * spring-cloud-starter-circuitbreaker-resilience4j pulls in ONLY
 * resilience4j-circuitbreaker and resilience4j-timelimiter. It does NOT pull in
 * resilience4j-spring-boot. That means:
 * <ul>
 *   <li>The {@code resilience4j.circuitbreaker.instances.*} YAML namespace does
 *       NOT bind. Configuring it in application.yml is a silent no-op.</li>
 *   <li>The {@code @CircuitBreaker} / {@code @Retry} annotations are NOT
 *       available.</li>
 *   <li>Instances are configured here, in Java, and used through
 *       {@code CircuitBreakerFactory}.</li>
 * </ul>
 *
 * <p>Retry needs an explicit {@code io.github.resilience4j:resilience4j-retry}
 * dependency, or a RestClient request interceptor. Decide once and do it the
 * same way in every service — see docs/contracts.md.
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
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
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
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build()), RESULTS_PROVIDER);
    }
}
