package com.scoregrid.prediction.prediction.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedOutcomeTest {

    @Test
    void homeWin() {
        assertThat(DerivedOutcome.from(2, 1)).isEqualTo(DerivedOutcome.HOME_WIN);
        assertThat(DerivedOutcome.from(3, 0)).isEqualTo(DerivedOutcome.HOME_WIN);
    }

    @Test
    void draw() {
        assertThat(DerivedOutcome.from(1, 1)).isEqualTo(DerivedOutcome.DRAW);
        assertThat(DerivedOutcome.from(0, 0)).isEqualTo(DerivedOutcome.DRAW);
    }

    @Test
    void awayWin() {
        assertThat(DerivedOutcome.from(1, 2)).isEqualTo(DerivedOutcome.AWAY_WIN);
        assertThat(DerivedOutcome.from(0, 3)).isEqualTo(DerivedOutcome.AWAY_WIN);
    }
}
