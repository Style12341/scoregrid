package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.model.ScoringRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringRuleV1Test {

    private final ScoringRule rule = new ScoringRuleV1();

    @ParameterizedTest
    @CsvSource(textBlock = """
            # predictedHome, predictedAway, actualHome, actualAway, expectedPoints, hit, exactHit
            2, 1, 2, 1, 3, true, true
            2, 1, 3, 0, 1, true, false
            1, 1, 2, 2, 1, true, false
            2, 1, 1, 2, 0, false, false
            0, 0, 0, 0, 3, true, true
            3, 3, 3, 3, 3, true, true
            0, 1, 0, 2, 1, true, false
            1, 0, 2, 0, 1, true, false
            0, 2, 1, 0, 0, false, false
            1, 3, 2, 3, 1, true, false
            """)
    void score(int predictedHome, int predictedAway, int actualHome, int actualAway,
               int expectedPoints, boolean hit, boolean exactHit) {
        var predicted = new ScoringRule.PredictedResult(predictedHome, predictedAway);
        var actual = new ScoringRule.ActualResult(actualHome, actualAway, deriveOutcome(actualHome, actualAway));

        int points = rule.score(predicted, actual);

        assertThat(points).isEqualTo(expectedPoints);
        assertThat(rule.isHit(points)).isEqualTo(hit);
        assertThat(rule.isExactHit(points)).isEqualTo(exactHit);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            # predictedHome, predictedAway, actualHome, actualAway
            2, 1, 2, 1
            0, 0, 0, 0
            """)
    void exactDoesNotAddOutcomePoints(int ph, int pa, int ah, int aa) {
        var predicted = new ScoringRule.PredictedResult(ph, pa);
        var actual = new ScoringRule.ActualResult(ah, aa, deriveOutcome(ah, aa));

        int points = rule.score(predicted, actual);

        assertThat(points).isEqualTo(3)
                .withFailMessage("Exact score must award 3 points, not 4 (exact + outcome)");
    }

    private String deriveOutcome(int home, int away) {
        if (home > away) return "HOME_WIN";
        if (home < away) return "AWAY_WIN";
        return "DRAW";
    }
}
