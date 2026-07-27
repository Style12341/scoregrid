package com.scoregrid.score.score.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.port.out.ScoreEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
class ScoreEventPublisherAdapter implements ScoreEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScoreEventPublisherAdapter.class);
    private static final String EXCHANGE = "scoregrid.events";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    ScoreEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Override
    public void scoreCalculated(MatchScore matchScore) {
        try {
            Map<String, Object> payload = Map.of(
                    "matchId", matchScore.matchId(),
                    "tournamentId", matchScore.tournamentId() != null ? matchScore.tournamentId() : "",
                    "scoredPredictions", matchScore.scoredPredictions(),
                    "totalPointsAwarded", matchScore.totalPointsAwarded(),
                    "calculatedAt", matchScore.calculatedAt().toString()
            );

            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "score.calculated",
                    "occurredAt", Instant.now().toString(),
                    "version", 1,
                    "payload", payload
            );

            byte[] message = objectMapper.writeValueAsBytes(envelope);
            rabbitTemplate.send(EXCHANGE, "score.calculated",
                    new org.springframework.amqp.core.Message(message));
            log.info("Event published: score.calculated matchId={}", matchScore.matchId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize score.calculated event", e);
        }
    }
}
