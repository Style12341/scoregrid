package com.scoregrid.tournament.match.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-08-14T18:30:00Z");
    private static final Instant PAST = Instant.parse("2026-07-01T00:00:00Z");

    private final TeamRef home = TeamRef.of(7L, "Argentina", "ARG");
    private final TeamRef away = TeamRef.of(8L, "Brazil", "BRA");

    @Nested
    class Creation {

        @Test
        void shouldCreateGroupMatch() {
            var match = Match.create(1L, 3L, null, home, away, FUTURE, NOW);

            assertThat(match.getTournamentId()).isEqualTo(1L);
            assertThat(match.getGroupId()).isEqualTo(3L);
            assertThat(match.getPhaseId()).isNull();
            assertThat(match.getHomeTeam()).isEqualTo(home);
            assertThat(match.getAwayTeam()).isEqualTo(away);
            assertThat(match.getStartTime()).isEqualTo(FUTURE);
            assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(match.getHomeScore()).isNull();
            assertThat(match.getAwayScore()).isNull();
        }

        @Test
        void shouldCreatePhaseMatch() {
            var match = Match.create(1L, null, 5L, home, away, FUTURE, NOW);

            assertThat(match.getGroupId()).isNull();
            assertThat(match.getPhaseId()).isEqualTo(5L);
            assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        }

        @Test
        void shouldRejectSameHomeAndAway() {
            assertThatThrownBy(() -> Match.create(1L, 3L, null, home, home, FUTURE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Home team and away team must differ");
        }

        @Test
        void shouldRejectNeitherGroupNorPhase() {
            assertThatThrownBy(() -> Match.create(1L, null, null, home, away, FUTURE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exactly one of groupId or phaseId is required");
        }

        @Test
        void shouldRejectBothGroupAndPhase() {
            assertThatThrownBy(() -> Match.create(1L, 3L, 5L, home, away, FUTURE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exactly one of groupId or phaseId is required");
        }

        @Test
        void shouldRejectStartTimeInPast() {
            assertThatThrownBy(() -> Match.create(1L, 3L, null, home, away, PAST, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startTime must be in the future");
        }

        @Test
        void shouldRejectStartTimeEqualToNow() {
            assertThatThrownBy(() -> Match.create(1L, 3L, null, home, away, NOW, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startTime must be in the future");
        }
    }

    @Nested
    class StateMachine {

        @Test
        void shouldStartFromScheduled() {
            var match = scheduledMatch();
            match.start();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        }

        @Test
        void shouldFinishFromScheduled() {
            var match = scheduledMatch();
            match.finish();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        }

        @Test
        void shouldFinishFromInProgress() {
            var match = inProgressMatch();
            match.finish();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        }

        @Test
        void shouldPostponeFromScheduled() {
            var match = scheduledMatch();
            match.postpone();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.POSTPONED);
        }

        @Test
        void shouldCancelFromScheduled() {
            var match = scheduledMatch();
            match.cancel();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        }

        @Test
        void shouldCancelFromPostponed() {
            var match = postponedMatch();
            match.cancel();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        }

        @Test
        void shouldRescheduleFromPostponed() {
            var match = postponedMatch();
            var newTime = Instant.parse("2026-08-20T20:00:00Z");
            match.reschedule(newTime);
            assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(match.getStartTime()).isEqualTo(newTime);
        }

        @Test
        void shouldRejectTransitionFromFinished() {
            var match = finishedMatch();
            assertThatThrownBy(() -> match.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid transition: FINISHED → IN_PROGRESS");
        }

        @Test
        void shouldRejectTransitionFromCancelled() {
            var match = cancelledMatch();
            assertThatThrownBy(() -> match.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid transition: CANCELLED → IN_PROGRESS");
        }

        @Test
        void shouldRejectPostponeFromInProgress() {
            var match = inProgressMatch();
            assertThatThrownBy(match::postpone)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IN_PROGRESS");
        }

        @Test
        void shouldRejectCancelFromInProgress() {
            var match = inProgressMatch();
            assertThatThrownBy(match::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IN_PROGRESS");
        }

        @Test
        void shouldRejectStartFromPostponed() {
            var match = postponedMatch();
            assertThatThrownBy(match::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("POSTPONED");
        }

        @Test
        void shouldRejectFinishFromPostponed() {
            var match = postponedMatch();
            assertThatThrownBy(match::finish)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("POSTPONED");
        }
    }

    @Nested
    class LoadResult {

        @Test
        void shouldLoadResultHomeWin() {
            var match = scheduledMatch();
            var outcome = match.loadResult(2, 1);

            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(match.getHomeScore()).isEqualTo(2);
            assertThat(match.getAwayScore()).isEqualTo(1);
            assertThat(outcome).isEqualTo("HOME_WIN");
            assertThat(match.outcome()).isEqualTo("HOME_WIN");
        }

        @Test
        void shouldLoadResultAwayWin() {
            var match = inProgressMatch();
            var outcome = match.loadResult(0, 3);

            assertThat(outcome).isEqualTo("AWAY_WIN");
        }

        @Test
        void shouldLoadResultDraw() {
            var match = scheduledMatch();
            var outcome = match.loadResult(1, 1);

            assertThat(outcome).isEqualTo("DRAW");
        }

        @Test
        void shouldResubmitResult() {
            var match = finishedMatch();
            match.loadResult(3, 1);

            assertThat(match.getHomeScore()).isEqualTo(3);
            assertThat(match.outcome()).isEqualTo("HOME_WIN");
        }

        @Test
        void shouldRejectResultOnPostponed() {
            var match = postponedMatch();
            assertThatThrownBy(() -> match.loadResult(2, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot load result for a match in status POSTPONED");
        }

        @Test
        void shouldRejectResultOnCancelled() {
            var match = cancelledMatch();
            assertThatThrownBy(() -> match.loadResult(2, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot load result for a match in status CANCELLED");
        }

        @Test
        void shouldRejectNegativeScore() {
            var match = scheduledMatch();
            assertThatThrownBy(() -> match.loadResult(-1, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Scores must be between 0 and 99");
        }

        @Test
        void shouldRejectScoreOver99() {
            var match = scheduledMatch();
            assertThatThrownBy(() -> match.loadResult(100, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Scores must be between 0 and 99");
        }
    }

    @Nested
    class ViewHelpers {

        @Test
        void isInProgressFalseWhenStatusNotScheduled() {
            var match = finishedMatch();
            assertThat(match.isInProgress(NOW)).isFalse();
        }

        @Test
        void isInProgressTrueWhenScheduledAndPastKickoff() {
            var match = Match.reconstitute(99L, 1L, 3L, null, home, away,
                    PAST, MatchStatus.SCHEDULED, null, null);
            assertThat(match.isInProgress(NOW)).isTrue();
        }

        @Test
        void isInProgressFalseWhenScheduledAndFutureKickoff() {
            var match = scheduledMatch();
            assertThat(match.isInProgress(NOW)).isFalse();
        }

        @Test
        void isInProgressTrueExactlyAtKickoff() {
            var match = Match.reconstitute(99L, 1L, 3L, null, home, away,
                    NOW, MatchStatus.SCHEDULED, null, null);
            assertThat(match.isInProgress(NOW)).isTrue();
        }

        @Test
        void predictionsOpenTrueForFutureScheduledMatch() {
            var match = scheduledMatch();
            assertThat(match.isPredictionsOpen(NOW)).isTrue();
        }

        @Test
        void predictionsOpenFalseForPastScheduledMatch() {
            var match = Match.reconstitute(99L, 1L, 3L, null, home, away,
                    PAST, MatchStatus.SCHEDULED, null, null);
            assertThat(match.isPredictionsOpen(NOW)).isFalse();
        }

        @Test
        void predictionsOpenFalseForInProgress() {
            var match = inProgressMatch();
            assertThat(match.isPredictionsOpen(NOW)).isFalse();
        }

        @Test
        void predictionsOpenFalseForFinished() {
            var match = finishedMatch();
            assertThat(match.isPredictionsOpen(NOW)).isFalse();
        }

        @Test
        void predictionsOpenFalseForPostponed() {
            var match = postponedMatch();
            assertThat(match.isPredictionsOpen(NOW)).isFalse();
        }

        @Test
        void predictionsOpenFalseForCancelled() {
            var match = cancelledMatch();
            assertThat(match.isPredictionsOpen(NOW)).isFalse();
        }
    }

    // -- helpers ---------------------------------------------------------------

    private Match scheduledMatch() {
        return Match.create(1L, 3L, null, home, away, FUTURE, NOW);
    }

    private Match inProgressMatch() {
        var m = scheduledMatch();
        m.start();
        return m;
    }

    private Match finishedMatch() {
        var m = scheduledMatch();
        m.loadResult(2, 1);
        return m;
    }

    private Match postponedMatch() {
        var m = scheduledMatch();
        m.postpone();
        return m;
    }

    private Match cancelledMatch() {
        var m = scheduledMatch();
        m.cancel();
        return m;
    }
}
