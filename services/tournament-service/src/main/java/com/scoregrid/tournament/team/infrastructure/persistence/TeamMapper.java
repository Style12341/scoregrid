package com.scoregrid.tournament.team.infrastructure.persistence;

import com.scoregrid.tournament.team.domain.model.Team;

final class TeamMapper {

    private TeamMapper() {
    }

    static Team toDomain(TeamJpaEntity entity) {
        var team = Team.create(entity.getName(), entity.getShortName(),
                entity.getCountry(), entity.getLogoUrl());
        team.setId(entity.getId());
        return team;
    }

    static TeamJpaEntity toEntity(Team team) {
        var entity = new TeamJpaEntity();
        entity.setId(team.getId());
        entity.setName(team.getName());
        entity.setShortName(team.getShortName());
        entity.setCountry(team.getCountry());
        entity.setLogoUrl(team.getLogoUrl());
        return entity;
    }
}
