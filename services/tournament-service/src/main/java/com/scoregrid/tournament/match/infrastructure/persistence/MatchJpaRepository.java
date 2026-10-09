package com.scoregrid.tournament.match.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchJpaRepository extends JpaRepository<MatchEntity, Long> {

    // The entity graph joins both teams into the match query. Without it,
    // Hibernate loads the EAGER team associations with one select per
    // distinct team after the list query (N+1).

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<MatchEntity> findByTournamentIdOrderByStartTimeAsc(Long tournamentId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<MatchEntity> findByTournamentIdAndStatusOrderByStartTimeAsc(Long tournamentId, String status);
}
