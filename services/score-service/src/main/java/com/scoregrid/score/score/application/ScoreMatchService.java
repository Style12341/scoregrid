package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.model.ScoredPrediction;
import com.scoregrid.score.score.domain.model.ScoringRule;
import com.scoregrid.score.score.domain.port.in.ScoreMatchUseCase;
import com.scoregrid.score.score.domain.port.out.MatchScoreRepository;
import com.scoregrid.score.score.domain.port.out.PredictionClientPort;
import com.scoregrid.score.score.domain.port.out.ScoreEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
class ScoreMatchService implements ScoreMatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScoreMatchService.class);

    private final ScoringRule scoringRule;
    private final PredictionClientPort predictionClient;
    private final MatchScoreRepository matchScoreRepository;
    private final ScoreEventPublisher eventPublisher;

    ScoreMatchService(ScoringRule scoringRule,
                      PredictionClientPort predictionClient,
                      MatchScoreRepository matchScoreRepository,
                      ScoreEventPublisher eventPublisher) {
        this.scoringRule = scoringRule;
        this.predictionClient = predictionClient;
        this.matchScoreRepository = matchScoreRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public MatchScore scoreMatch(String matchId, String tournamentId,
                                  int homeScore, int awayScore, String outcome) {
        var actual = new ScoringRule.ActualResult(homeScore, awayScore, outcome);
        var predictions = predictionClient.getPredictionsForMatch(matchId);

        List<ScoredPrediction> individualScores = predictions.stream()
                .map(p -> scoreOne(p, actual))
                .toList();

        int totalPoints = individualScores.stream().mapToInt(ScoredPrediction::points).sum();

        MatchScore matchScore = new MatchScore(
                matchId,
                tournamentId,
                homeScore,
                awayScore,
                outcome,
                individualScores.size(),
                totalPoints,
                Instant.now(),
                individualScores
        );

        MatchScore saved = matchScoreRepository.save(matchScore);
        log.info("Match scored: {} with {} predictions, {} total points",
                matchId, individualScores.size(), totalPoints);

        eventPublisher.scoreCalculated(saved);
        return saved;
    }

    private ScoredPrediction scoreOne(PredictionClientPort.PredictionResult prediction,
                                       ScoringRule.ActualResult actual) {
        var predicted = new ScoringRule.PredictedResult(prediction.homeScore(), prediction.awayScore());
        int points = scoringRule.score(predicted, actual);
        return new ScoredPrediction(
                prediction.userId(),
                prediction.predictionId(),
                prediction.homeScore(),
                prediction.awayScore(),
                points,
                scoringRule.isHit(points),
                scoringRule.isExactHit(points)
        );
    }
}
