package com.scoregrid.tournament.phase.infrastructure.persistence;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.model.PhaseType;

final class PhaseMapper {

    private PhaseMapper() {
    }

    static Phase toDomain(PhaseJpaEntity entity) {
        return Phase.reconstitute(
                entity.getId(),
                entity.getTournamentId(),
                entity.getName(),
                PhaseType.valueOf(entity.getType()),
                entity.getDisplayOrder());
    }

    static PhaseJpaEntity toEntity(Phase phase) {
        var entity = new PhaseJpaEntity();
        entity.setId(phase.getId());
        entity.setTournamentId(phase.getTournamentId());
        entity.setName(phase.getName());
        entity.setType(phase.getType().name());
        entity.setDisplayOrder(phase.getDisplayOrder());
        return entity;
    }
}
