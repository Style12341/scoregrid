package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.model.ScoringRule;
import org.springframework.stereotype.Component;

@Component
class ScoringRuleV1 implements ScoringRule {

    @Override
    public int score(PredictedResult predicted, ActualResult actual) {
        // Exact score: 3 points. Exact implies outcome — award 3, not 4.
        if (predicted.homeScore() == actual.homeScore()
                && predicted.awayScore() == actual.awayScore()) {
            return EXACT_SCORE_POINTS;
        }

        // Correct outcome but not exact: 1 point.
        if (Integer.signum(predicted.goalDifference()) == Integer.signum(actual.goalDifference())) {
            return CORRECT_OUTCOME_POINTS;
        }

        return MISS_POINTS;
    }
}
