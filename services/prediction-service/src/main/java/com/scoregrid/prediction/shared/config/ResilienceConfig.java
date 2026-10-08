package com.scoregrid.prediction.shared.config;

import com.scoregrid.prediction.shared.error.DomainException;
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
 * Circuit breaker, retry and time limit for this service's outbound calls.
 *
 * <p>Configured here in Java, not under {@code resilience4j.*} in YAML, like
 * every other service (AGENTS.md §5). One HTTP request to tournament-service
 * runs as: breaker, then time limiter, then retry, then the request itself
 * through the {@code @LoadBalanced} RestClient (see TournamentRestClient).
 *
 * <p>The numbers are one budget. The gateway gives a proxied request 5 s
 * ({@code spring.http.clients.read-timeout} there), and creating a prediction
 * on a match-cache miss makes three sequential requests here. With one
 * replica dead and round robin alternating, each request can spend one
 * connect timeout on it before its retry lands on the live one:
 * 3 x (0.5 s + 0.2 s) plus three fast reads, about 2.2 s with the default
 * {@code scoregrid.clients.*} timeouts, well under the gateway's 5 s.
 *
 * <p>Known limit: when tournament-service is slow rather than dead (reads
 * near or past the 1.5 s read timeout on every request), the three requests
 * can add up to more than 5 s. The gateway then answers 503 while this
 * service carries on and saves the prediction, so the participant sees a
 * failure for a write that happened, and a resend gets 409
 * DUPLICATE_PREDICTION. Tighter per-request numbers cannot close that; it
 * needs a deadline for the whole use case or an idempotency key.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /** Named instance for this service's outbound dependency: tournamentClient */
    public static final String TOURNAMENT_CLIENT = "tournamentClient";

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
     * {@code @PostConstruct} that can run after TournamentRestClient's
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

    /**
     * A DomainException is ignored: neither a success nor a failure. It is
     * how TournamentRestClient reports a 4xx from tournament-service, such as
     * a 404 for a match id that does not exist. That is an answer from a
     * healthy service; counting it would let any participant open the breaker
     * by predicting on made-up match ids, and every prediction would then be
     * refused with 503.
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> tournamentClientCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .ignoreExceptions(DomainException.class)
                        .build()), TOURNAMENT_CLIENT);
    }

    /**
     * Retries one HTTP request. Not retried: a DomainException, which is how
     * TournamentRestClient reports a 4xx (a 404 for an unknown match): asking
     * again gets the same answer. Retried: everything else, a 5xx included,
     * and connect or read failures. Every attempt is a new request through
     * the LoadBalancer, so after a dead replica the next attempt goes to the
     * other one.
     *
     * <p>Each retry logs one WARN line, so a failover shows up in Loki.
     */
    @Bean
    Retry tournamentClientRetry() {
        Retry retry = Retry.of(TOURNAMENT_CLIENT, RetryConfig.custom()
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
     * breaker at Resilience4J's 1 s default. That 1 s cut the retry off before
     * it reached the live replica.
     */
    @Bean
    TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(TIME_LIMIT)
                .build());
    }
}
