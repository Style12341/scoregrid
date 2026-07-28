package com.scoregrid.prediction.prediction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort;
import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort.CachedMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class MatchCacheConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchCacheConsumer.class);

    private final MatchCachePort matchCache;
    private final ObjectMapper objectMapper;

    MatchCacheConsumer(MatchCachePort matchCache) {
        this.matchCache = matchCache;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @RabbitListener(queues = "prediction.match-cache")
    void handleMatchEvent(MatchEventEnvelope envelope) {
        log.debug("Received event: {} for match", envelope.eventType());

        try {
            MatchScheduledPayload payload = objectMapper.convertValue(envelope.payload(), MatchScheduledPayload.class);

            boolean predictionsOpen = "ACTIVE".equals(payload.tournamentStatus())
                    && "SCHEDULED".equals(payload.status());

            CachedMatch cached = new CachedMatch(
                    payload.matchId(),
                    payload.tournamentId(),
                    payload.tournamentStatus(),
                    payload.status(),
                    payload.startTime(),
                    predictionsOpen
            );

            matchCache.put(cached);
            log.info("Match cache updated: {} status={}", payload.matchId(), payload.status());
        } catch (Exception e) {
            log.error("Failed to deserialize match event payload: {}", envelope.eventType(), e);
            throw e;
        }
    }
}
