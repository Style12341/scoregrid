package com.scoregrid.prediction.prediction.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

record MatchResponse(
        @JsonProperty("id") String id,
        @JsonProperty("tournamentId") String tournamentId,
        @JsonProperty("groupId") String groupId,
        @JsonProperty("phaseId") String phaseId,
        @JsonProperty("homeTeam") Object homeTeam,
        @JsonProperty("awayTeam") Object awayTeam,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("status") String status,
        @JsonProperty("homeScore") Object homeScore,
        @JsonProperty("awayScore") Object awayScore,
        @JsonProperty("predictionsOpen") boolean predictionsOpen
) {}
