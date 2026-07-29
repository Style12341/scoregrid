package com.scoregrid.tournament.match.domain.port.in;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;

import java.time.Instant;

public interface UpdateMatch {
    record Command(Long id, Long groupId, Long phaseId,
                   Long homeTeamId, Long awayTeamId,
                   Instant startTime, MatchStatus status) {}
    Match execute(Command command);
}
