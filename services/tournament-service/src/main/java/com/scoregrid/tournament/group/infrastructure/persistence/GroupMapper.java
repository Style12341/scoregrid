package com.scoregrid.tournament.group.infrastructure.persistence;

import com.scoregrid.tournament.group.domain.model.Group;

final class GroupMapper {

    private GroupMapper() {
    }

    static Group toDomain(GroupJpaEntity entity) {
        return Group.reconstitute(
                entity.getId(),
                entity.getTournamentId(),
                entity.getName(),
                entity.getDisplayOrder());
    }

    static GroupJpaEntity toEntity(Group group) {
        var entity = new GroupJpaEntity();
        entity.setId(group.getId());
        entity.setTournamentId(group.getTournamentId());
        entity.setName(group.getName());
        entity.setDisplayOrder(group.getDisplayOrder());
        return entity;
    }
}
