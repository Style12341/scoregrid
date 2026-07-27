package com.scoregrid.prediction.prediction.domain.port.out;

import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort.CachedMatch;

public interface TournamentClientPort {

    CachedMatch getMatch(String matchId);

    boolean isUserEnrolled(String tournamentId, String userId);
}
