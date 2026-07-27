package com.scoregrid.tournament.team.infrastructure.persistence;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class TeamRepositoryAdapter implements TeamRepository {

    private final TeamJpaRepository jpaRepository;

    TeamRepositoryAdapter(TeamJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Team save(Team team) {
        var entity = TeamMapper.toEntity(team);
        var saved = jpaRepository.save(entity);
        return TeamMapper.toDomain(saved);
    }

    @Override
    public Optional<Team> findById(Long id) {
        return jpaRepository.findById(id).map(TeamMapper::toDomain);
    }

    @Override
    public List<Team> findAll() {
        return jpaRepository.findAll().stream()
                .map(TeamMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public List<Team> findAllById(List<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(TeamMapper::toDomain)
                .toList();
    }
}
