package com.scoregrid.score.score.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

record PredictionServiceResponse(
        @JsonProperty("id") String id,
        @JsonProperty("userId") String userId,
        @JsonProperty("tournamentId") String tournamentId,
        @JsonProperty("matchId") String matchId,
        @JsonProperty("predictionType") String predictionType,
        @JsonProperty("homeScore") int homeScore,
        @JsonProperty("awayScore") int awayScore,
        @JsonProperty("derivedOutcome") String derivedOutcome,
        @JsonProperty("locked") boolean locked,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
