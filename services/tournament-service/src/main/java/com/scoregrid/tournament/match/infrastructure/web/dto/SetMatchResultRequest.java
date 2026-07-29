package com.scoregrid.tournament.match.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SetMatchResultRequest(
        @Min(0) @Max(99) int homeScore,
        @Min(0) @Max(99) int awayScore
) {}
