package com.scoregrid.tournament.group.infrastructure.persistence;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class GroupRepositoryAdapter implements GroupRepository {

    private final GroupJpaRepository jpaRepository;

    GroupRepositoryAdapter(GroupJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Group save(Group group) {
        var entity = GroupMapper.toEntity(group);
        var saved = jpaRepository.save(entity);
        return GroupMapper.toDomain(saved);
    }

    @Override
    public List<Group> findByTournamentId(Long tournamentId) {
        return jpaRepository.findByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .map(GroupMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Group> findById(Long id) {
        return jpaRepository.findById(id).map(GroupMapper::toDomain);
    }
}
