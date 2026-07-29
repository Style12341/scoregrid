package com.scoregrid.tournament.team.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TournamentTeamJpaRepository extends JpaRepository<TournamentTeamJpaEntity, TournamentTeamJpaEntity.TournamentTeamId> {

    List<TournamentTeamJpaEntity> findByTournamentId(Long tournamentId);

    @Query("SELECT tt.teamId FROM TournamentTeamJpaEntity tt WHERE tt.tournamentId = :tournamentId")
    List<Long> findTeamIdsByTournamentId(Long tournamentId);

    boolean existsByTournamentId(Long tournamentId);

    boolean existsByTournamentIdAndTeamId(Long tournamentId, Long teamId);
}
