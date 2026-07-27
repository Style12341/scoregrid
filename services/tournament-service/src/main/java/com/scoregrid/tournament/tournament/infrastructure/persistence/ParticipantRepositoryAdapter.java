package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ParticipantRepositoryAdapter implements ParticipantRepository {

    private final ParticipantJpaRepository jpaRepository;

    ParticipantRepositoryAdapter(ParticipantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Participant save(Participant participant) {
        var entity = ParticipantMapper.toEntity(participant);
        var saved = jpaRepository.save(entity);
        return ParticipantMapper.toDomain(saved);
    }

    @Override
    public Optional<Participant> find(Long tournamentId, String userId) {
        var id = new ParticipantJpaEntity.ParticipantId(tournamentId, userId);
        return jpaRepository.findById(id).map(ParticipantMapper::toDomain);
    }

    @Override
    public List<Participant> findByTournamentId(Long tournamentId) {
        return jpaRepository.findByTournamentId(tournamentId).stream()
                .map(ParticipantMapper::toDomain)
                .toList();
    }

    @Override
    public boolean exists(Long tournamentId, String userId) {
        var id = new ParticipantJpaEntity.ParticipantId(tournamentId, userId);
        return jpaRepository.existsById(id);
    }
}
