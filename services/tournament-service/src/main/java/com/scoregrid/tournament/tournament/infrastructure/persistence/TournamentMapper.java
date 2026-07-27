package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;

final class TournamentMapper {

    private TournamentMapper() {
    }

    static Tournament toDomain(TournamentJpaEntity entity) {
        return Tournament.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                TournamentStatus.valueOf(entity.getStatus()),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static TournamentJpaEntity toEntity(Tournament tournament) {
        var entity = new TournamentJpaEntity();
        entity.setId(tournament.getId());
        entity.setName(tournament.getName());
        entity.setDescription(tournament.getDescription());
        entity.setStatus(tournament.getStatus().name());
        entity.setStartDate(tournament.getStartDate());
        entity.setEndDate(tournament.getEndDate());
        entity.setCreatedBy(tournament.getCreatedBy());
        entity.setCreatedAt(tournament.getCreatedAt());
        entity.setUpdatedAt(tournament.getUpdatedAt());
        return entity;
    }
}
