package com.scoregrid.score.shared.config;

import com.scoregrid.score.shared.error.DomainException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * Circuit breaker, retry and time limit for the call to prediction-service.
 *
 * <p>Configured here in Java, not under {@code resilience4j.*} in YAML, like
 * every other service (AGENTS.md §5). One HTTP request runs as: breaker, then
 * time limiter, then retry, then the request itself through the
 * {@code @LoadBalanced} RestClient.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /** Named instance for this service's outbound dependency: predictionClient */
    public static final String PREDICTION_CLIENT = "predictionClient";

    /** Attempts per HTTP request, the first one included. */
    private static final int MAX_ATTEMPTS = 3;

    /** Wait before the second attempt; it doubles before the third: 200 ms, 400 ms. */
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(200);

    /**
     * 0.5 s connect timeout to a dead replica + 0.2 s back-off + 1.5 s read on
     * the live one (scoregrid.clients.*); every attempt dead is less:
     * 3 x 0.5 s + 0.2 s + 0.4 s = 2.1 s.
     */
    private static final Duration TIME_LIMIT = Duration.ofMillis(2200);

    /**
     * Hands the factory the ObservationRegistry while it is being built. The
     * time limiter runs each call on another thread, and only an observed
     * breaker carries the trace over to it; without that the outbound request
     * starts a new trace. Spring Cloud sets the registry itself, but in a
     * {@code @PostConstruct} that can run after PredictionRestClient's
     * constructor has already created its breaker, unobserved.
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> observedCircuitBreakerCustomizer(
            ObservationRegistry observationRegistry) {
        return factory -> factory.setObservationRegistry(observationRegistry);
    }

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
    Customizer<Resilience4JCircuitBreakerFactory> predictionClientCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build()), PREDICTION_CLIENT);
    }

    /**
     * Retries one HTTP request on any failure but a DomainException. Every
     * attempt is a new request through the LoadBalancer, so after a dead
     * replica the next attempt goes to the other one. Each retry logs one
     * WARN line, so a failover shows up in Loki.
     */
    @Bean
    Retry predictionClientRetry() {
        Retry retry = Retry.of(PREDICTION_CLIENT, RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(FIRST_BACKOFF, 2))
                .retryExceptions(Exception.class)
                .ignoreExceptions(DomainException.class)
                .build());
        retry.getEventPublisher().onRetry(event -> log.warn("Retrying {} request (retry {} of {}) after {}",
                event.getName(), event.getNumberOfRetryAttempts(), MAX_ATTEMPTS - 1,
                causeName(event.getLastThrowable())));
        return retry;
    }

    /**
     * The failure without RestClient's ResourceAccessException around it, which
     * says nothing on its own: ConnectException, HttpConnectTimeoutException,
     * HttpTimeoutException (read), InternalServerError (a 5xx).
     */
    private static String causeName(Throwable failure) {
        Throwable cause = failure instanceof ResourceAccessException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return cause.getClass().getSimpleName();
    }

    /**
     * The breakers' time limiter, as the registry default. Not set through
     * {@code configureDefault}/{@code configure} above: Spring Cloud
     * CircuitBreaker 5.0.2 takes the time limiter only from this registry and
     * ignores the builder's {@code timeLimiterConfig}, silently leaving every
     * breaker at Resilience4J's 1 s default, which cuts the retry off before
     * it reaches a live replica.
     */
    @Bean
    TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(TIME_LIMIT)
                .build());
    }
}
