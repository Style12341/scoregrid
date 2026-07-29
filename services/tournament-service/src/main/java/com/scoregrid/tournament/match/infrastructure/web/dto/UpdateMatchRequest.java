package com.scoregrid.tournament.match.infrastructure.web.dto;

import com.scoregrid.tournament.match.domain.model.MatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateMatchRequest(
        String groupId,
        String phaseId,
        @NotBlank String homeTeamId,
        @NotBlank String awayTeamId,
        @NotNull Instant startTime,
        @NotNull MatchStatus status
) {}
