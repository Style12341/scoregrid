package com.scoregrid.score.score.infrastructure.client;

import com.scoregrid.score.score.domain.port.out.PredictionClientPort;
import com.scoregrid.score.shared.error.DomainException;
import com.scoregrid.score.shared.error.ErrorKind;
import com.scoregrid.score.shared.security.ServiceTokenInterceptor;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.NestedExceptionUtils;
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
                         ServiceTokenInterceptor tokenInterceptor,
                         @LoadBalanced RestClient.Builder restClientBuilder,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                         Retry predictionClientRetry) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerFactory.create(PREDICTION_CLIENT);
        this.retry = predictionClientRetry;
    }

    @Override
    public List<PredictionResult> getPredictionsForMatch(String matchId) {
        return execute("match " + matchId, () -> {
            List<PredictionServiceResponse> responses = restClient.get()
                    .uri("/api/predictions/match/{matchId}", matchId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (responses == null) return List.of();

            return responses.stream()
                    .map(r -> new PredictionResult(r.userId(), r.id(), r.homeScore(), r.awayScore()))
                    .toList();
        });
    }

    /**
     * One HTTP request, with the breaker, time limiter and retry around it.
     * Each attempt goes through the {@code @LoadBalanced} client's
     * interceptor, which asks the LoadBalancer for an instance again.
     */
    private <T> T execute(String resource, Supplier<T> request) {
        Supplier<T> withRetry = Retry.decorateSupplier(retry, request);
        return circuitBreaker.run(withRetry, failure -> {
            if (failure instanceof DomainException domainException) {
                throw domainException;
            }
            // One line, no stack trace: during an outage this fires on every
            // request. The innermost cause also names an open circuit.
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
            log.warn("prediction-service call for {} failed: {}: {}", resource,
                    cause.getClass().getSimpleName(), cause.getMessage());
            throw new DomainException(ErrorKind.DOWNSTREAM_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                    "Prediction service is currently unavailable.");
        });
    }
}
