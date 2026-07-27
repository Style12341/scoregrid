package com.scoregrid.prediction.prediction.infrastructure.persistence;

import com.scoregrid.prediction.prediction.domain.model.DerivedOutcome;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.model.PredictionType;

final class PredictionMapper {

    private PredictionMapper() {
    }

    static Prediction toDomain(PredictionDocument doc) {
        return new Prediction(
                doc.getId(),
                doc.getUserId(),
                doc.getTournamentId(),
                doc.getMatchId(),
                PredictionType.valueOf(doc.getPredictionType()),
                doc.getHomeScore(),
                doc.getAwayScore(),
                DerivedOutcome.valueOf(doc.getDerivedOutcome()),
                doc.isLocked(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    static PredictionDocument toDocument(Prediction prediction) {
        return new PredictionDocument(
                prediction.id(),
                prediction.userId(),
                prediction.tournamentId(),
                prediction.matchId(),
                prediction.predictionType().name(),
                prediction.homeScore(),
                prediction.awayScore(),
                prediction.derivedOutcome().name(),
                prediction.locked(),
                prediction.createdAt(),
                prediction.updatedAt()
        );
    }
}
