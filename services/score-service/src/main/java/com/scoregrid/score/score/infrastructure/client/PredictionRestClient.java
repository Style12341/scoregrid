package com.scoregrid.score.score.infrastructure.client;

import com.scoregrid.score.score.domain.port.out.PredictionClientPort;
import com.scoregrid.score.shared.error.DomainException;
import com.scoregrid.score.shared.error.ErrorKind;
import com.scoregrid.score.shared.security.ServiceToken;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

import static com.scoregrid.score.shared.config.ResilienceConfig.PREDICTION_CLIENT;

@Component
class PredictionRestClient implements PredictionClientPort {

    private static final Logger log = LoggerFactory.getLogger(PredictionRestClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    PredictionRestClient(@Value("${scoregrid.clients.prediction.base-url}") String baseUrl,
                         @Value("${scoregrid.jwt.secret}") String jwtSecret,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                         Retry predictionClientRetry) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + ServiceToken.generate(jwtSecret, "score-service"))
                .build();
        this.circuitBreaker = circuitBreakerFactory.create(PREDICTION_CLIENT);
        this.retry = predictionClientRetry;
    }

    @Override
    public List<PredictionResult> getPredictionsForMatch(String matchId) {
        return executeWithResilience(() -> {
            List<PredictionServiceResponse> responses = restClient.get()
                    .uri("/api/predictions/match/{matchId}", matchId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (responses == null) return List.of();

            return responses.stream()
                    .map(r -> new PredictionResult(r.userId(), r.id(), r.homeScore(), r.awayScore()))
                    .toList();
        }, matchId);
    }

    private <T> T executeWithResilience(Supplier<T> supplier, String resourceId) {
        Supplier<T> withRetry = Retry.decorateSupplier(retry, supplier);
        try {
            return circuitBreaker.run(withRetry, throwable -> {
                log.error("Circuit open for prediction-client, resource: {}", resourceId, throwable);
                throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                        "Prediction service is currently unavailable.");
            });
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call prediction-service for match: {}", resourceId, e);
            throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                    "Prediction service is currently unavailable.");
        }
    }
}
