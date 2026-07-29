package com.scoregrid.tournament.phase.domain.port.in;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.model.PhaseType;

public interface CreatePhase {
    record Command(Long tournamentId, PhaseType type, String name, int displayOrder) {}
    Phase execute(Command command);
}
