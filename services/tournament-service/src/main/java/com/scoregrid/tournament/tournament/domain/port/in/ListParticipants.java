package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Participant;

import java.util.List;

public interface ListParticipants {
    List<Participant> execute(Long tournamentId);
}
