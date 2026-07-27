package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.tournament.domain.model.Participant;

final class ParticipantMapper {

    private ParticipantMapper() {
    }

    static Participant toDomain(ParticipantJpaEntity entity) {
        return new Participant(entity.getTournamentId(), entity.getUserId(), entity.getJoinedAt());
    }

    static ParticipantJpaEntity toEntity(Participant participant) {
        var entity = new ParticipantJpaEntity();
        entity.setTournamentId(participant.getTournamentId());
        entity.setUserId(participant.getUserId());
        entity.setJoinedAt(participant.getJoinedAt());
        return entity;
    }
}
