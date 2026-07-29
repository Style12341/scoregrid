package com.scoregrid.tournament.phase.infrastructure.web.dto;

import com.scoregrid.tournament.phase.domain.model.PhaseType;
import jakarta.validation.constraints.NotNull;

public record CreatePhaseRequest(
        @NotNull PhaseType type,
        String name,
        int displayOrder
) {}
