package com.scoregrid.tournament.match.domain.port.in;

public interface SetMatchResultUseCase {
    record Command(Long id, int homeScore, int awayScore) {}
    void execute(Command command);
}
