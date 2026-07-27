package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Participant;

public interface GetParticipant {
    Participant execute(Long tournamentId, String userId);
}
