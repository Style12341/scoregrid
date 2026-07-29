package com.scoregrid.tournament.phase.infrastructure.persistence;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class PhaseRepositoryAdapter implements PhaseRepository {

    private final PhaseJpaRepository jpaRepository;

    PhaseRepositoryAdapter(PhaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Phase save(Phase phase) {
        var entity = PhaseMapper.toEntity(phase);
        var saved = jpaRepository.save(entity);
        return PhaseMapper.toDomain(saved);
    }

    @Override
    public List<Phase> findByTournamentId(Long tournamentId) {
        return jpaRepository.findByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .map(PhaseMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Phase> findById(Long id) {
        return jpaRepository.findById(id).map(PhaseMapper::toDomain);
    }
}
