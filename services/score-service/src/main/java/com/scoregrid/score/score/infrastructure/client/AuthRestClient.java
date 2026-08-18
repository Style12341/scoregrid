package com.scoregrid.score.score.infrastructure.client;

import com.scoregrid.score.score.domain.port.out.AuthClientPort;
import com.scoregrid.score.shared.security.ServiceTokenInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class AuthRestClient implements AuthClientPort {

    private static final Logger log = LoggerFactory.getLogger(AuthRestClient.class);

    private final RestClient restClient;

    AuthRestClient(@Value("${scoregrid.clients.auth.base-url}") String baseUrl,
                   ServiceTokenInterceptor tokenInterceptor,
                   @LoadBalanced RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor(tokenInterceptor)
                .build();
    }

    @Override
    public Map<String, String> getUsernames(List<String> userIds) {
        if (userIds.isEmpty()) return Map.of();

        String ids = String.join(",", userIds);
        try {
            List<BatchUserResponse> responses = restClient.get()
                    .uri("/api/users/batch?ids={ids}", ids)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (responses == null) return Map.of();

            return responses.stream()
                    .collect(Collectors.toMap(BatchUserResponse::id, BatchUserResponse::username));
        } catch (Exception e) {
            log.warn("Failed to resolve usernames for {} users: {}", userIds.size(), e.getMessage());
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> id));
        }
    }
}
