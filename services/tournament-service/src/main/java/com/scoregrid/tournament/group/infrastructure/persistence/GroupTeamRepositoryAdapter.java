package com.scoregrid.tournament.group.infrastructure.persistence;

import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class GroupTeamRepositoryAdapter implements GroupTeamRepository {

    private final GroupTeamJpaRepository jpaRepository;
    private final TeamRepository teamRepository;

    GroupTeamRepositoryAdapter(GroupTeamJpaRepository jpaRepository,
                               TeamRepository teamRepository) {
        this.jpaRepository = jpaRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    public void assign(Long groupId, Long teamId) {
        jpaRepository.save(new GroupTeamJpaEntity(groupId, teamId));
    }

    @Override
    public boolean existsByGroupIdAndTeamId(Long groupId, Long teamId) {
        return jpaRepository.existsByGroupIdAndTeamId(groupId, teamId);
    }

    @Override
    public Optional<Long> findGroupIdByTeamIdAndTournamentId(Long teamId, Long tournamentId) {
        return jpaRepository.findGroupIdByTeamIdAndTournamentId(teamId, tournamentId);
    }

    @Override
    public List<Team> findTeamsByGroupId(Long groupId) {
        var teamIds = jpaRepository.findByGroupId(groupId)
                .stream()
                .map(GroupTeamJpaEntity::getTeamId)
                .toList();
        if (teamIds.isEmpty()) return List.of();
        return teamRepository.findAllById(teamIds);
    }
}
