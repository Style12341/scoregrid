package com.scoregrid.score.score.domain.port.in;

import com.scoregrid.score.score.domain.model.MatchScore;

public interface ScoreMatchUseCase {

    MatchScore scoreMatch(String matchId, String tournamentId, int homeScore, int awayScore, String outcome);
}
