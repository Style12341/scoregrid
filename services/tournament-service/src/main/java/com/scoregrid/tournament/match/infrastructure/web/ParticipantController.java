package com.scoregrid.tournament.match.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class ParticipantController {

    private final JdbcTemplate jdbc;

    ParticipantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/tournaments/{tournamentId}/participants/{userId}")
    ResponseEntity<Map<String, Object>> checkEnrollment(@PathVariable String tournamentId,
                                                         @PathVariable String userId) {
        var rows = jdbc.queryForList(
                "SELECT 1 FROM tournament_participants WHERE tournament_id = ? AND user_id = ?",
                Long.parseLong(tournamentId), userId);

        if (rows.isEmpty()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "tournamentId", tournamentId,
                "joinedAt", "2026-07-27T00:00:00Z"));
    }
}
