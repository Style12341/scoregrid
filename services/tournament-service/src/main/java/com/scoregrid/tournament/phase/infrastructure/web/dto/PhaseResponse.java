package com.scoregrid.tournament.phase.infrastructure.web.dto;

import com.scoregrid.tournament.phase.domain.model.Phase;

public record PhaseResponse(
        String id,
        String tournamentId,
        String name,
        String type,
        int displayOrder
) {
    public static PhaseResponse from(Phase p) {
        return new PhaseResponse(
                p.getId().toString(),
                p.getTournamentId().toString(),
                p.getName(),
                p.getType().name(),
                p.getDisplayOrder());
    }
}
