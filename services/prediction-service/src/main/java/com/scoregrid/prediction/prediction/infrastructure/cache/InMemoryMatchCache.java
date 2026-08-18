package com.scoregrid.prediction.prediction.infrastructure.cache;

import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
class InMemoryMatchCache implements MatchCachePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMatchCache.class);

    private final Map<String, CachedMatch> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedMatch> get(String matchId) {
        CachedMatch match = cache.get(matchId);
        if (match != null
                && match.predictionsOpen()
                && "SCHEDULED".equals(match.matchStatus())
                && match.startTime() != null
                && !Instant.now().isBefore(match.startTime())) {
            cache.remove(matchId, match);
            log.debug("Cache expired at kickoff for match {}", matchId);
            return Optional.empty();
        }
        return Optional.ofNullable(match);
    }

    @Override
    public void put(CachedMatch match) {
        cache.put(match.matchId(), match);
        log.debug("Cache updated for match {}", match.matchId());
    }

    @Override
    public void evict(String matchId) {
        cache.remove(matchId);
        log.debug("Cache evicted for match {}", matchId);
    }
}
