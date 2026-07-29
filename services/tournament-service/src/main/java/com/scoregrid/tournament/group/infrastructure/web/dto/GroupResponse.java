package com.scoregrid.tournament.group.infrastructure.web.dto;

import com.scoregrid.tournament.group.domain.model.Group;

public record GroupResponse(
        String id,
        String tournamentId,
        String name,
        int displayOrder
) {
    public static GroupResponse from(Group g) {
        return new GroupResponse(
                g.getId().toString(),
                g.getTournamentId().toString(),
                g.getName(),
                g.getDisplayOrder());
    }
}
