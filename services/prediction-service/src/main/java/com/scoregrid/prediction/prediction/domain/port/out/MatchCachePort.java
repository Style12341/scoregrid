package com.scoregrid.prediction.prediction.domain.port.out;

import java.time.Instant;
import java.util.Optional;

public interface MatchCachePort {

    Optional<CachedMatch> get(String matchId);

    void put(CachedMatch match);

    void evict(String matchId);

    record CachedMatch(
            String matchId,
            String tournamentId,
            String tournamentStatus,
            String matchStatus,
            Instant startTime
    ) {
        public boolean predictionsOpen() {
            return "ACTIVE".equals(tournamentStatus)
                    && "SCHEDULED".equals(matchStatus)
                    && Instant.now().isBefore(startTime);
        }
    }
}
