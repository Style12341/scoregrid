package com.scoregrid.tournament.team.infrastructure.persistence;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class TournamentTeamRepositoryAdapter implements TournamentTeamRepository {

    private final TournamentTeamJpaRepository jpaRepository;
    private final TeamJpaRepository teamJpaRepository;

    TournamentTeamRepositoryAdapter(TournamentTeamJpaRepository jpaRepository,
                                     TeamJpaRepository teamJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.teamJpaRepository = teamJpaRepository;
    }

    @Override
    public void assign(Long tournamentId, Long teamId) {
        var id = new TournamentTeamJpaEntity.TournamentTeamId(tournamentId, teamId);
        if (!jpaRepository.existsById(id)) {
            jpaRepository.save(new TournamentTeamJpaEntity(tournamentId, teamId));
        }
    }

    @Override
    public List<Team> findByTournamentId(Long tournamentId) {
        var teamIds = jpaRepository.findTeamIdsByTournamentId(tournamentId);
        return teamJpaRepository.findAllById(teamIds).stream()
                .map(TeamMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByTournamentId(Long tournamentId) {
        return jpaRepository.existsByTournamentId(tournamentId);
    }

    @Override
    public boolean existsByTournamentIdAndTeamId(Long tournamentId, Long teamId) {
        return jpaRepository.existsByTournamentIdAndTeamId(tournamentId, teamId);
    }
}
