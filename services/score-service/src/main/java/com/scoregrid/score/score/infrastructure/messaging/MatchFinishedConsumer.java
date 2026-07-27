package com.scoregrid.score.score.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scoregrid.score.score.application.ScoreMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class MatchFinishedConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchFinishedConsumer.class);

    private final ScoreMatchService scoreMatchService;
    private final ObjectMapper objectMapper;

    MatchFinishedConsumer(ScoreMatchService scoreMatchService) {
        this.scoreMatchService = scoreMatchService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @RabbitListener(queues = "score.match-finished")
    void handleMatchFinished(ScoreEventEnvelope envelope) {
        log.info("Received match.finished event: {}", envelope.eventId());

        try {
            MatchFinishedPayload payload = objectMapper.convertValue(
                    envelope.payload(), MatchFinishedPayload.class);

            scoreMatchService.scoreMatch(
                    payload.matchId(),
                    payload.tournamentId(),
                    payload.homeScore(),
                    payload.awayScore(),
                    payload.outcome());

            log.info("Match {} scored successfully", payload.matchId());
        } catch (Exception e) {
            log.error("Failed to process match.finished event: {}", envelope.eventId(), e);
            throw e; // Let RabbitMQ retry/DLQ handle it
        }
    }
}
