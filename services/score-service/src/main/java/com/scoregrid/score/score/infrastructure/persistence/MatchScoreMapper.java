package com.scoregrid.score.score.infrastructure.persistence;

import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.model.ScoredPrediction;

final class MatchScoreMapper {

    private MatchScoreMapper() {
    }

    static MatchScore toDomain(MatchScoreDocument doc) {
        return new MatchScore(
                doc.getMatchId(),
                doc.getTournamentId(),
                doc.getHomeScore(),
                doc.getAwayScore(),
                doc.getOutcome(),
                doc.getScoredPredictions(),
                doc.getTotalPointsAwarded(),
                doc.getCalculatedAt(),
                doc.getIndividualScores().stream()
                        .map(e -> new ScoredPrediction(
                                e.getUserId(),
                                e.getPredictionId(),
                                e.getPredictedHomeScore(),
                                e.getPredictedAwayScore(),
                                e.getPoints(),
                                e.isHit(),
                                e.isExactHit()))
                        .toList()
        );
    }

    static MatchScoreDocument toDocument(MatchScore matchScore) {
        return new MatchScoreDocument(
                matchScore.matchId(),
                matchScore.tournamentId(),
                matchScore.homeScore(),
                matchScore.awayScore(),
                matchScore.outcome(),
                matchScore.scoredPredictions(),
                matchScore.totalPointsAwarded(),
                matchScore.calculatedAt(),
                matchScore.individualScores().stream()
                        .map(s -> new ScoredPredictionEmbedded(
                                s.userId(),
                                s.predictionId(),
                                s.predictedHomeScore(),
                                s.predictedAwayScore(),
                                s.points(),
                                s.hit(),
                                s.exactHit()))
                        .toList()
        );
    }
}
