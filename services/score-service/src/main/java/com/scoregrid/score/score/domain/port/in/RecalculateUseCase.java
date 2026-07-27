package com.scoregrid.score.score.domain.port.in;

public interface RecalculateUseCase {

    void recalculateMatch(String matchId);

    void recalculateTournament(String tournamentId);
}
