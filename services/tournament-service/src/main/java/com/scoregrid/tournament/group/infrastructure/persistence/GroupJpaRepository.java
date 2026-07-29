package com.scoregrid.tournament.group.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupJpaRepository extends JpaRepository<GroupJpaEntity, Long> {

    List<GroupJpaEntity> findByTournamentIdOrderByDisplayOrderAsc(Long tournamentId);
}
