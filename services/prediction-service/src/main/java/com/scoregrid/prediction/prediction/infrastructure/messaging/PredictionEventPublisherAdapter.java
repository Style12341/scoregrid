package com.scoregrid.prediction.prediction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
class PredictionEventPublisherAdapter implements PredictionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PredictionEventPublisherAdapter.class);
    private static final String EXCHANGE = "scoregrid.events";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    PredictionEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void predictionCreated(Prediction prediction) {
        publish("prediction.created", prediction);
    }

    @Override
    public void predictionUpdated(Prediction prediction) {
        publish("prediction.updated", prediction);
    }

    private void publish(String routingKey, Prediction prediction) {
        try {
            Map<String, Object> payload = Map.of(
                    "predictionId", prediction.id(),
                    "userId", prediction.userId(),
                    "tournamentId", prediction.tournamentId(),
                    "matchId", prediction.matchId(),
                    "homeScore", prediction.homeScore(),
                    "awayScore", prediction.awayScore(),
                    "derivedOutcome", prediction.derivedOutcome().name()
            );

            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", routingKey,
                    "occurredAt", Instant.now().toString(),
                    "version", 1,
                    "payload", payload
            );

            byte[] message = objectMapper.writeValueAsBytes(envelope);
            rabbitTemplate.send(EXCHANGE, routingKey,
                    new org.springframework.amqp.core.Message(message));
            log.info("Event published: {} predictionId={}", routingKey, prediction.id());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {}", routingKey, e);
        }
    }
}
