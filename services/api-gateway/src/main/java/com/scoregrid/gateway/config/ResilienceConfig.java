package com.scoregrid.gateway.config;

import com.scoregrid.gateway.filter.ReadRetryFilterFunctions;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers behind each route's {@code CircuitBreaker} filter, one per
 * downstream service (the filter's {@code id} in application.yml).
 *
 * <p>Configured here in Java, not under {@code resilience4j.*} in YAML, like
 * every other service (AGENTS.md §5).
 *
 * <p>Values are sized for a live demo: the breaker opens after a handful of
 * failed calls and probes again after ten seconds, so "service down" turns
 * into an instant 503 quickly and recovery is visible without waiting a minute.
 */
@Configuration
public class ResilienceConfig {

    /** 2 s connect timeout to a dead replica + 1 s RetryReads back-off + 5 s read on the live one. */
    private static final Duration TIME_LIMIT = Duration.ofSeconds(8);

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> gatewayCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build())
                .build());
    }

    /**
     * The breakers' time limiter, as the registry default. Not set through
     * {@code configureDefault} above: Spring Cloud CircuitBreaker 5.0.2 takes
     * the time limiter only from this registry and ignores the builder's
     * {@code timeLimiterConfig}, silently leaving every breaker at
     * Resilience4J's 1 s default, which would cut a failover short. A read
     * timeout is never retried, so no path needs room for two reads.
     */
    @Bean
    TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(TIME_LIMIT)
                .build());
    }

    /** Makes {@code RetryReads} available to the routes in application.yml. */
    @Bean
    ReadRetryFilterFunctions.FilterSupplier readRetryFilterSupplier() {
        return new ReadRetryFilterFunctions.FilterSupplier();
    }
}
