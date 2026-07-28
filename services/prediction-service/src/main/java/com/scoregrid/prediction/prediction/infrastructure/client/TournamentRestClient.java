package com.scoregrid.prediction.prediction.infrastructure.client;

import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort.CachedMatch;
import com.scoregrid.prediction.prediction.domain.port.out.TournamentClientPort;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import com.scoregrid.prediction.shared.security.ServiceTokenInterceptor;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static com.scoregrid.prediction.shared.config.ResilienceConfig.TOURNAMENT_CLIENT;

@Component
class TournamentRestClient implements TournamentClientPort {

    private static final Logger log = LoggerFactory.getLogger(TournamentRestClient.class);
    private static final String MATCH_PATH = "/api/matches/{id}";
    private static final String ENROLLMENT_PATH = "/api/tournaments/{tournamentId}/participants/{userId}";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    TournamentRestClient(@Value("${scoregrid.clients.tournament.base-url}") String baseUrl,
                         ServiceTokenInterceptor tokenInterceptor,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                         Retry tournamentClientRetry) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerFactory.create(TOURNAMENT_CLIENT);
        this.retry = tournamentClientRetry;
    }

    @Override
    public CachedMatch getMatch(String matchId) {
        return executeWithResilience(() -> {
            MatchResponse match = restClient.get()
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
                    .body(MatchResponse.class);

            return new CachedMatch(
                    match.id(),
                    match.tournamentId(),
                    match.predictionsOpen() ? "ACTIVE" : null,
                    match.status(),
                    match.startTime(),
                    match.predictionsOpen()
            );
        }, matchId);
    }

    @Override
    public boolean isUserEnrolled(String tournamentId, String userId) {
        return executeWithResilience(() -> {
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
        }, tournamentId + "/" + userId);
    }

    private <T> T executeWithResilience(Supplier<T> supplier, String resourceId) {
        Supplier<T> withRetry = Retry.decorateSupplier(retry, supplier);
        try {
            return circuitBreaker.run(withRetry, throwable -> {
                log.error("Circuit open for tournament-client, resource: {}", resourceId, throwable);
                throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                        "Tournament service is currently unavailable. Please try again later.");
            });
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call tournament-service for resource: {}", resourceId, e);
            throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                    "Tournament service is currently unavailable. Please try again later.");
        }
    }
}
