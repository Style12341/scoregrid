package com.scoregrid.tournament.tournament.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record StatusTransitionRequest(
        @NotNull String status
) {}
