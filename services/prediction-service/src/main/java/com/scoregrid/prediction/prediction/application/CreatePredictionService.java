package com.scoregrid.prediction.prediction.application;

import com.scoregrid.prediction.prediction.domain.model.DerivedOutcome;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.model.PredictionType;
import com.scoregrid.prediction.prediction.domain.port.in.CreatePredictionUseCase;
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
class CreatePredictionService implements CreatePredictionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePredictionService.class);

    private final PredictionRepository predictionRepository;
    private final MatchCachePort matchCache;
    private final TournamentClientPort tournamentClient;
    private final PredictionEventPublisher eventPublisher;

    CreatePredictionService(PredictionRepository predictionRepository,
                            MatchCachePort matchCache,
                            TournamentClientPort tournamentClient,
                            PredictionEventPublisher eventPublisher) {
        this.predictionRepository = predictionRepository;
        this.matchCache = matchCache;
        this.tournamentClient = tournamentClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Prediction create(String userId, String matchId, int homeScore, int awayScore) {
        // Step 2: match exists in cache or via REST
        CachedMatch match = matchCache.get(matchId)
                .orElseGet(() -> {
                    CachedMatch fetched = tournamentClient.getMatch(matchId);
                    matchCache.put(fetched);
                    return fetched;
                });

        // Step 3: predictions must be open
        if (!match.predictionsOpen()) {
            throw new DomainException(ErrorKind.CONFLICT, "PREDICTION_LOCKED",
                    "Match has already started; predictions are locked.");
        }

        // Step 4: tournament must be ACTIVE (already checked by predictionsOpen)

        // Step 5: user must be enrolled
        if (!tournamentClient.isUserEnrolled(match.tournamentId(), userId)) {
            throw new DomainException(ErrorKind.FORBIDDEN, "NOT_ENROLLED",
                    "User is not enrolled in this tournament.");
        }

        // Step 6: duplicate check — unique index is the real enforcer, but we check first for a clean error
        if (predictionRepository.existsByUserIdAndMatchId(userId, matchId)) {
            throw new DomainException(ErrorKind.CONFLICT, "DUPLICATE_PREDICTION",
                    "You already have a prediction for this match.");
        }

        DerivedOutcome outcome = DerivedOutcome.from(homeScore, awayScore);
        boolean locked = !match.predictionsOpen();
        Instant now = Instant.now();

        Prediction prediction = new Prediction(
                null,
                userId,
                match.tournamentId(),
                matchId,
                PredictionType.EXACT_SCORE,
                homeScore,
                awayScore,
                outcome,
                locked,
                now,
                now
        );

        Prediction saved = predictionRepository.save(prediction);
        log.info("Prediction created: userId={} matchId={} id={}", userId, matchId, saved.id());
        eventPublisher.predictionCreated(saved);
        return saved;
    }
}
