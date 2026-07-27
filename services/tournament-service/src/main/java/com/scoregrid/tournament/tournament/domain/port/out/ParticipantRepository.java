package com.scoregrid.tournament.tournament.domain.port.out;

import com.scoregrid.tournament.tournament.domain.model.Participant;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository {
    Participant save(Participant participant);

    Optional<Participant> find(Long tournamentId, String userId);

    List<Participant> findByTournamentId(Long tournamentId);

    boolean exists(Long tournamentId, String userId);
}
