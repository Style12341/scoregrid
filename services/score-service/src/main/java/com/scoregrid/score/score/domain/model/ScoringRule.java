package com.scoregrid.score.score.domain.model;

public interface ScoringRule {

    int EXACT_SCORE_POINTS = 3;
    int CORRECT_OUTCOME_POINTS = 1;
    int MISS_POINTS = 0;

    int score(PredictedResult predicted, ActualResult actual);

    default boolean isHit(int points) {
        return points > 0;
    }

    default boolean isExactHit(int points) {
        return points == EXACT_SCORE_POINTS;
    }

    record PredictedResult(int homeScore, int awayScore) {
        public int goalDifference() {
            return homeScore - awayScore;
        }
    }

    record ActualResult(int homeScore, int awayScore, String outcome) {
        public int goalDifference() {
            return homeScore - awayScore;
        }
    }
}
