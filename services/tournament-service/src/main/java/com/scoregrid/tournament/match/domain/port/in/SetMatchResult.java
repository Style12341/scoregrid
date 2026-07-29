package com.scoregrid.tournament.match.domain.port.in;

public interface SetMatchResult {
    record Command(Long id, int homeScore, int awayScore) {}
    void execute(Command command);
}
