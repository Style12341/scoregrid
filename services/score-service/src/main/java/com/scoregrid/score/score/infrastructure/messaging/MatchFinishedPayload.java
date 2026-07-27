package com.scoregrid.score.score.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

record MatchFinishedPayload(
        @JsonProperty("matchId") String matchId,
        @JsonProperty("tournamentId") String tournamentId,
        @JsonProperty("homeTeamId") String homeTeamId,
        @JsonProperty("awayTeamId") String awayTeamId,
        @JsonProperty("homeScore") int homeScore,
        @JsonProperty("awayScore") int awayScore,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("finishedAt") Instant finishedAt
) {}
