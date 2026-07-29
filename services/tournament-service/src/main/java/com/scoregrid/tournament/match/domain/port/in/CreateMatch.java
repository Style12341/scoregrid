package com.scoregrid.tournament.match.domain.port.in;

import com.scoregrid.tournament.match.domain.model.Match;

import java.time.Instant;

public interface CreateMatch {
    record Command(Long tournamentId, Long groupId, Long phaseId,
                   Long homeTeamId, Long awayTeamId, Instant startTime) {}
    Match execute(Command command);
}
