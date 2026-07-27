package com.scoregrid.prediction.prediction.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

record MatchScheduledPayload(
        @JsonProperty("matchId") String matchId,
        @JsonProperty("tournamentId") String tournamentId,
        @JsonProperty("tournamentStatus") String tournamentStatus,
        @JsonProperty("groupId") String groupId,
        @JsonProperty("phaseId") String phaseId,
        @JsonProperty("homeTeamId") String homeTeamId,
        @JsonProperty("awayTeamId") String awayTeamId,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("status") String status
) {}
