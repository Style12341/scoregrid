package com.scoregrid.tournament.tournament.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantJpaEntity, ParticipantJpaEntity.ParticipantId> {

    List<ParticipantJpaEntity> findByTournamentId(Long tournamentId);
}
