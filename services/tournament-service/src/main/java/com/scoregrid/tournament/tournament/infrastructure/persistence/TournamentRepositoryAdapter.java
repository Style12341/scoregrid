package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class TournamentRepositoryAdapter implements TournamentRepository {

    private final TournamentJpaRepository jpaRepository;

    TournamentRepositoryAdapter(TournamentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Tournament save(Tournament tournament) {
        var entity = TournamentMapper.toEntity(tournament);
        var saved = jpaRepository.save(entity);
        return TournamentMapper.toDomain(saved);
    }

    @Override
    public Optional<Tournament> findById(Long id) {
        return jpaRepository.findById(id).map(TournamentMapper::toDomain);
    }

    @Override
    public List<Tournament> findAllByStatus(TournamentStatus status, int offset, int limit) {
        int page = offset / limit;
        return jpaRepository.findAllByStatus(status.name(), PageRequest.of(page, limit))
                .stream()
                .map(TournamentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Tournament> findAllPaginated(int offset, int limit) {
        int page = offset / limit;
        return jpaRepository.findAll(PageRequest.of(page, limit))
                .stream()
                .map(TournamentMapper::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(TournamentStatus status) {
        return jpaRepository.countByStatus(status.name());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
