package com.scoregrid.tournament.team.infrastructure.web.dto;

import com.scoregrid.tournament.team.domain.model.Team;

public record TeamResponse(
        String id,
        String name,
        String shortName,
        String country,
        String logoUrl
) {
    public static TeamResponse from(Team t) {
        return new TeamResponse(
                t.getId().toString(),
                t.getName(),
                t.getShortName(),
                t.getCountry(),
                t.getLogoUrl());
    }
}
