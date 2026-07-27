package com.scoregrid.prediction.prediction.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

record EnrollmentResponse(
        @JsonProperty("userId") String userId,
        @JsonProperty("tournamentId") String tournamentId,
        @JsonProperty("joinedAt") Instant joinedAt
) {}
