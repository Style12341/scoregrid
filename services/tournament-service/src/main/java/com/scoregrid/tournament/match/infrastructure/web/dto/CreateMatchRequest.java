package com.scoregrid.tournament.match.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateMatchRequest(
        String groupId,
        String phaseId,
        @NotBlank String homeTeamId,
        @NotBlank String awayTeamId,
        @NotNull Instant startTime
) {}
