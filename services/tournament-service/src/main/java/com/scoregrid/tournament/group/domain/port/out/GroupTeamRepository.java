package com.scoregrid.tournament.group.domain.port.out;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;
import java.util.Optional;

public interface GroupTeamRepository {
    void assign(Long groupId, Long teamId);

    boolean existsByGroupIdAndTeamId(Long groupId, Long teamId);

    /**
     * Returns the group ID for a team within a tournament, if the team
     * is already assigned to some group in that tournament.
     */
    Optional<Long> findGroupIdByTeamIdAndTournamentId(Long teamId, Long tournamentId);

    List<Team> findTeamsByGroupId(Long groupId);
}
