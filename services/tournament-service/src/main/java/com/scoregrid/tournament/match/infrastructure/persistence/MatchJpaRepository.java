package com.scoregrid.tournament.match.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchJpaRepository extends JpaRepository<MatchEntity, Long> {

    List<MatchEntity> findByTournamentIdOrderByStartTimeAsc(Long tournamentId);

    List<MatchEntity> findByTournamentIdAndStatusOrderByStartTimeAsc(Long tournamentId, String status);
}
