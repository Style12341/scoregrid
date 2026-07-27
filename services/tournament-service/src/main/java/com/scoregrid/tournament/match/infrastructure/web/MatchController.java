// STREAM C STUB — Replace when building match feature (Paggi, Stream B).
// Known issues to fix during replacement:
//   - Line 44: Re-derives predictionsOpen from Instant.now() — must trust tournament-service's computation (AGENTS.md hard rule 5)
//   - Lines 69-103: PUT /api/matches/{id}/result missing @PreAuthorize("hasRole('ADMIN')") — contracts.md requires ADMIN
//   - Lines 42, 72: Raw ResponseEntity.notFound() bypasses GlobalExceptionHandler — use DomainException(NOT_FOUND, "NOT_FOUND", ...)
//   - See docs/review-stream-c.md findings C5 and C8 for the same patterns caught in prediction-service

package com.scoregrid.tournament.match.infrastructure.web;

import com.scoregrid.tournament.match.infrastructure.persistence.MatchEntity;
import com.scoregrid.tournament.match.infrastructure.persistence.MatchRepository;
import com.scoregrid.tournament.shared.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
class MatchController {

    private final MatchRepository matchRepository;
    private final RabbitTemplate rabbitTemplate;

    MatchController(MatchRepository matchRepository, RabbitTemplate rabbitTemplate) {
        this.matchRepository = matchRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/{id}")
    ResponseEntity<Map<String, Object>> getMatch(@PathVariable Long id) {
        var match = matchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();

        boolean predictionsOpen = "SCHEDULED".equals(match.getStatus())
                && Instant.now().isBefore(match.getStartTime());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", match.getId().toString());
        response.put("tournamentId", match.getTournamentId().toString());
        response.put("groupId", match.getGroupId() != null ? match.getGroupId().toString() : null);
        response.put("phaseId", match.getPhaseId() != null ? match.getPhaseId().toString() : null);
        response.put("homeTeam", Map.of(
                "id", match.getHomeTeam().getId().toString(),
                "name", match.getHomeTeam().getName(),
                "shortName", match.getHomeTeam().getShortName()));
        response.put("awayTeam", Map.of(
                "id", match.getAwayTeam().getId().toString(),
                "name", match.getAwayTeam().getName(),
                "shortName", match.getAwayTeam().getShortName()));
        response.put("startTime", match.getStartTime().toString());
        response.put("status", match.getStatus());
        response.put("homeScore", match.getHomeScore());
        response.put("awayScore", match.getAwayScore());
        response.put("predictionsOpen", predictionsOpen);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/result")
    ResponseEntity<Void> setResult(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        var match = matchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();

        int homeScore = body.get("homeScore");
        int awayScore = body.get("awayScore");
        String outcome = homeScore > awayScore ? "HOME_WIN" : homeScore < awayScore ? "AWAY_WIN" : "DRAW";

        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus("FINISHED");
        matchRepository.save(match);

        Map<String, Object> payload = Map.of(
                "matchId", match.getId().toString(),
                "tournamentId", match.getTournamentId().toString(),
                "homeTeamId", match.getHomeTeam().getId().toString(),
                "awayTeamId", match.getAwayTeam().getId().toString(),
                "homeScore", homeScore,
                "awayScore", awayScore,
                "outcome", outcome,
                "finishedAt", Instant.now().toString());

        Map<String, Object> envelope = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "match.finished",
                "occurredAt", Instant.now().toString(),
                "version", 1,
                "payload", payload);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "match.finished", envelope);

        return ResponseEntity.noContent().build();
    }
}
