package com.scoregrid.prediction.prediction.application;

import com.scoregrid.prediction.prediction.domain.model.DerivedOutcome;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.model.PredictionType;
import com.scoregrid.prediction.prediction.domain.port.in.UpdatePredictionUseCase;
import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort;
import com.scoregrid.prediction.prediction.domain.port.out.MatchCachePort.CachedMatch;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionEventPublisher;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionRepository;
import com.scoregrid.prediction.prediction.domain.port.out.TournamentClientPort;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
class UpdatePredictionService implements UpdatePredictionUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdatePredictionService.class);

    private final PredictionRepository predictionRepository;
    private final MatchCachePort matchCache;
    private final TournamentClientPort tournamentClient;
    private final PredictionEventPublisher eventPublisher;

    UpdatePredictionService(PredictionRepository predictionRepository,
                            MatchCachePort matchCache,
                            TournamentClientPort tournamentClient,
                            PredictionEventPublisher eventPublisher) {
        this.predictionRepository = predictionRepository;
        this.matchCache = matchCache;
        this.tournamentClient = tournamentClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Prediction update(String userId, String predictionId, int homeScore, int awayScore) {
        Prediction existing = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Prediction not found."));

        if (!existing.userId().equals(userId)) {
            throw new DomainException(ErrorKind.FORBIDDEN, "FORBIDDEN",
                    "You can only update your own predictions.");
        }

        // Re-validate the match state
        CachedMatch match = matchCache.get(existing.matchId())
                .orElseGet(() -> {
                    CachedMatch fetched = tournamentClient.getMatch(existing.matchId());
                    matchCache.put(fetched);
                    return fetched;
                });

        if (!match.predictionsOpen()) {
            throw new DomainException(ErrorKind.CONFLICT, "PREDICTION_LOCKED",
                    "Match has already started; predictions are locked.");
        }

        DerivedOutcome outcome = DerivedOutcome.from(homeScore, awayScore);
        boolean locked = !match.predictionsOpen();

        Prediction updated = new Prediction(
                existing.id(),
                existing.userId(),
                existing.tournamentId(),
                existing.matchId(),
                PredictionType.EXACT_SCORE,
                homeScore,
                awayScore,
                outcome,
                locked,
                existing.createdAt(),
                Instant.now()
        );

        Prediction saved = predictionRepository.save(updated);
        log.info("Prediction updated: id={}", saved.id());
        eventPublisher.predictionUpdated(saved);
        return saved;
    }
}
