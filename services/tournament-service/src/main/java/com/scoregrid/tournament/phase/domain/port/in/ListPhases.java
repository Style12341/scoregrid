package com.scoregrid.tournament.phase.domain.port.in;

import com.scoregrid.tournament.phase.domain.model.Phase;

import java.util.List;

public interface ListPhases {
    List<Phase> execute(Long tournamentId);
}
