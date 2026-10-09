package com.scoregrid.tournament.match.infrastructure.messaging;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.shared.config.RabbitConfig;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
class MatchEventPublisherAdapter implements MatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MatchEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    MatchEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void scheduled(Match match, TournamentStatus tournamentStatus) {
        send("match.scheduled", scheduledPayload(match, tournamentStatus));
    }

    @Override
    public void updated(Match match, TournamentStatus tournamentStatus) {
        send("match.updated", scheduledPayload(match, tournamentStatus));
    }

    @Override
    public void finished(Match match) {
        send("match.finished", finishedPayload(match));
    }

    /**
     * One INFO line per event, so the demo (docs/demo.md, Observability tour)
     * can show the publish next to score-service's consume. The eventId is
     * what consumers deduplicate on, so it is the key for following one event
     * across the logs.
     */
    private void send(String routingKey, Map<String, Object> payload) {
        String eventId = UUID.randomUUID().toString();
        var envelope = Map.of(
                "eventId", eventId,
                "eventType", routingKey,
                "occurredAt", Instant.now().toString(),
                "version", 1,
                "payload", payload);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, envelope);
        log.info("Published {} matchId={} eventId={}", routingKey, payload.get("matchId"), eventId);
    }

    private Map<String, Object> scheduledPayload(Match match, TournamentStatus tournamentStatus) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("matchId", match.getId().toString());
        payload.put("tournamentId", match.getTournamentId().toString());
        payload.put("tournamentStatus", tournamentStatus.name());
        payload.put("groupId", match.getGroupId() != null ? match.getGroupId().toString() : null);
        payload.put("phaseId", match.getPhaseId() != null ? match.getPhaseId().toString() : null);
        payload.put("homeTeamId", match.getHomeTeam().id().toString());
        payload.put("awayTeamId", match.getAwayTeam().id().toString());
        payload.put("startTime", match.getStartTime().toString());
        payload.put("status", match.getStatus().name());
        return payload;
    }

    private Map<String, Object> finishedPayload(Match match) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("matchId", match.getId().toString());
        payload.put("tournamentId", match.getTournamentId().toString());
        payload.put("homeTeamId", match.getHomeTeam().id().toString());
        payload.put("awayTeamId", match.getAwayTeam().id().toString());
        payload.put("homeScore", match.getHomeScore());
        payload.put("awayScore", match.getAwayScore());
        payload.put("outcome", match.outcome());
        payload.put("finishedAt", Instant.now().toString());
        return payload;
    }
}
