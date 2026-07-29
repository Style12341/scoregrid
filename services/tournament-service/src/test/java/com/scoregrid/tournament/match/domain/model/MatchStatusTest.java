package com.scoregrid.tournament.match.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class MatchStatusTest {

    @Test
    void isTerminalShouldBeTrueForFinished() {
        assertThat(MatchStatus.FINISHED.isTerminal()).isTrue();
    }

    @Test
    void isTerminalShouldBeTrueForCancelled() {
        assertThat(MatchStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void isTerminalShouldBeFalseForScheduled() {
        assertThat(MatchStatus.SCHEDULED.isTerminal()).isFalse();
    }

    @Test
    void isTerminalShouldBeFalseForInProgress() {
        assertThat(MatchStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    void isTerminalShouldBeFalseForPostponed() {
        assertThat(MatchStatus.POSTPONED.isTerminal()).isFalse();
    }

    // -- valid transitions -----------------------------------------------------

    @Test
    void scheduledCanTransitionToInProgress() {
        assertThat(MatchStatus.SCHEDULED.canTransitionTo(MatchStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void scheduledCanTransitionToFinished() {
        assertThat(MatchStatus.SCHEDULED.canTransitionTo(MatchStatus.FINISHED)).isTrue();
    }

    @Test
    void scheduledCanTransitionToPostponed() {
        assertThat(MatchStatus.SCHEDULED.canTransitionTo(MatchStatus.POSTPONED)).isTrue();
    }

    @Test
    void scheduledCanTransitionToCancelled() {
        assertThat(MatchStatus.SCHEDULED.canTransitionTo(MatchStatus.CANCELLED)).isTrue();
    }

    @Test
    void inProgressCanTransitionToFinished() {
        assertThat(MatchStatus.IN_PROGRESS.canTransitionTo(MatchStatus.FINISHED)).isTrue();
    }

    @Test
    void postponedCanTransitionToScheduled() {
        assertThat(MatchStatus.POSTPONED.canTransitionTo(MatchStatus.SCHEDULED)).isTrue();
    }

    @Test
    void postponedCanTransitionToCancelled() {
        assertThat(MatchStatus.POSTPONED.canTransitionTo(MatchStatus.CANCELLED)).isTrue();
    }

    // -- invalid transitions (from SCHEDULED) ----------------------------------

    @Test
    void scheduledCannotTransitionToItself() {
        assertThat(MatchStatus.SCHEDULED.canTransitionTo(MatchStatus.SCHEDULED)).isFalse();
    }

    // -- invalid transitions (from IN_PROGRESS) --------------------------------

    @Test
    void inProgressCannotTransitionToScheduled() {
        assertThat(MatchStatus.IN_PROGRESS.canTransitionTo(MatchStatus.SCHEDULED)).isFalse();
    }

    @Test
    void inProgressCannotTransitionToPostponed() {
        assertThat(MatchStatus.IN_PROGRESS.canTransitionTo(MatchStatus.POSTPONED)).isFalse();
    }

    @Test
    void inProgressCannotTransitionToCancelled() {
        assertThat(MatchStatus.IN_PROGRESS.canTransitionTo(MatchStatus.CANCELLED)).isFalse();
    }

    @Test
    void inProgressCannotTransitionToItself() {
        assertThat(MatchStatus.IN_PROGRESS.canTransitionTo(MatchStatus.IN_PROGRESS)).isFalse();
    }

    // -- invalid transitions (from POSTPONED) ----------------------------------

    @Test
    void postponedCannotTransitionToInProgress() {
        assertThat(MatchStatus.POSTPONED.canTransitionTo(MatchStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void postponedCannotTransitionToFinished() {
        assertThat(MatchStatus.POSTPONED.canTransitionTo(MatchStatus.FINISHED)).isFalse();
    }

    // -- no transitions from terminal states -----------------------------------

    @ParameterizedTest
    @EnumSource(MatchStatus.class)
    void finishedCannotTransitionToAny(MatchStatus target) {
        assertThat(MatchStatus.FINISHED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(MatchStatus.class)
    void cancelledCannotTransitionToAny(MatchStatus target) {
        assertThat(MatchStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }
}
