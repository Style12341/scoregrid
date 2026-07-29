package com.scoregrid.tournament.team.domain.port.out;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface TournamentTeamRepository {
    void assign(Long tournamentId, Long teamId);

    List<Team> findByTournamentId(Long tournamentId);

    boolean existsByTournamentId(Long tournamentId);

    boolean existsByTournamentIdAndTeamId(Long tournamentId, Long teamId);
}
