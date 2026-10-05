package com.scoregrid.prediction.prediction.infrastructure.client;

import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort.CachedMatch;
import com.scoregrid.prediction.prediction.domain.port.out.TournamentClientPort;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import com.scoregrid.prediction.shared.security.ServiceTokenInterceptor;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.scoregrid.prediction.shared.config.ResilienceConfig.TOURNAMENT_CLIENT;

@Component
class TournamentRestClient implements TournamentClientPort {

    private static final Logger log = LoggerFactory.getLogger(TournamentRestClient.class);
    private static final String MATCH_PATH = "/api/matches/{id}";
    private static final String TOURNAMENT_PATH = "/api/tournaments/{id}";
    private static final String ENROLLMENT_PATH = "/api/tournaments/{tournamentId}/participants/{userId}";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    TournamentRestClient(@Value("${scoregrid.clients.tournament.base-url}") String baseUrl,
                         ServiceTokenInterceptor tokenInterceptor,
                         @LoadBalanced RestClient.Builder restClientBuilder,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                         Retry tournamentClientRetry) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerFactory.create(TOURNAMENT_CLIENT);
        this.retry = tournamentClientRetry;
    }

    @Override
    public CachedMatch getMatch(String matchId) {
        MatchResponse match = execute("match " + matchId, () -> restClient.get()
                .uri(MATCH_PATH, matchId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                                "Match " + matchId + " not found.");
                    }
                    throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                            "Tournament service returned error: " + res.getStatusCode());
                })
                .body(MatchResponse.class));

        String tournamentStatus = getTournamentStatus(match.tournamentId());
        boolean predictionsOpen = match.predictionsOpen() && "ACTIVE".equals(tournamentStatus);

        return new CachedMatch(
                match.id(),
                match.tournamentId(),
                tournamentStatus,
                match.status(),
                match.startTime(),
                predictionsOpen
        );
    }

    private String getTournamentStatus(String tournamentId) {
        TournamentResponse tournament = execute("tournament " + tournamentId, () -> restClient.get()
                .uri(TOURNAMENT_PATH, tournamentId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                                "Tournament " + tournamentId + " not found.");
                    }
                    throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE,
                            "DOWNSTREAM_UNAVAILABLE",
                            "Tournament service returned error: " + res.getStatusCode());
                })
                .body(TournamentResponse.class));
        return tournament.status();
    }

    @Override
    public boolean isUserEnrolled(String tournamentId, String userId) {
        return execute("enrolment " + tournamentId + "/" + userId, () -> {
            try {
                restClient.get()
                        .uri(ENROLLMENT_PATH, tournamentId, userId)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (HttpClientErrorException.NotFound e) {
                return false;
            }
        });
    }

    /**
     * One HTTP request, with the breaker, time limiter and retry around it.
     *
     * <p>Retried per request, not per use case. getMatch makes two requests,
     * and round robin alternates between replicas: retrying both together
     * would send the second one to the same dead replica on every attempt.
     * Each attempt goes through the {@code @LoadBalanced} client's
     * interceptor, which asks the LoadBalancer for an instance again.
     */
    private <T> T execute(String resource, Supplier<T> request) {
        Supplier<T> withRetry = Retry.decorateSupplier(retry, request);
        return circuitBreaker.run(withRetry, failure -> {
            if (failure instanceof DomainException domainException) {
                throw domainException;
            }
            logFailure(resource, failure);
            throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                    "Tournament service is currently unavailable. Please try again later.");
        });
    }

    /**
     * One line, no stack trace: during an outage this fires on every request.
     * "Circuit open" only when the breaker refused the call; otherwise the
     * innermost cause, which says more than the wrappers around it.
     */
    private static void logFailure(String resource, Throwable failure) {
        if (failure instanceof CallNotPermittedException) {
            log.warn("tournament-service not called for {}: circuit open", resource);
        } else if (failure instanceof TimeoutException) {
            log.warn("tournament-service call for {} cut by the time limiter: {}", resource, failure.getMessage());
        } else {
            log.warn("tournament-service call for {} failed: {}", resource, rootCause(failure));
        }
    }

    private static String rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
