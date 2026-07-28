package com.scoregrid.tournament.tournament.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateTournamentWithDefaultStatusDraft() {
            var tournament = Tournament.create("Copa 2026", "Desc", LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 15), "42");

            assertThat(tournament.getName()).isEqualTo("Copa 2026");
            assertThat(tournament.getDescription()).isEqualTo("Desc");
            assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.DRAFT);
            assertThat(tournament.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(tournament.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(tournament.getCreatedBy()).isEqualTo("42");
            assertThat(tournament.getCreatedAt()).isNotNull();
            assertThat(tournament.getUpdatedAt()).isNotNull();
        }

        @Test
        void shouldCreateTournamentWithoutOptionalFields() {
            var tournament = Tournament.create("Copa 2026", null, null, null, "42");

            assertThat(tournament.getName()).isEqualTo("Copa 2026");
            assertThat(tournament.getDescription()).isNull();
            assertThat(tournament.getStartDate()).isNull();
            assertThat(tournament.getEndDate()).isNull();
        }
    }

    @Nested
    class StateMachine {

        @Test
        void shouldTransitionDraftToActive() {
            var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(7), null, "42");
            tournament.transitionTo(TournamentStatus.ACTIVE);
            assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.ACTIVE);
        }

        @Test
        void shouldTransitionActiveToFinished() {
            var tournament = activeTournament();
            tournament.transitionTo(TournamentStatus.FINISHED);
            assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.FINISHED);
        }

        @Test
        void shouldTransitionActiveToCancelled() {
            var tournament = activeTournament();
            tournament.transitionTo(TournamentStatus.CANCELLED);
            assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.CANCELLED);
        }

        @ParameterizedTest
        @EnumSource(value = TournamentStatus.class, names = {"FINISHED", "CANCELLED"})
        void shouldRejectTransitionToTerminal(TournamentStatus target) {
            var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(1), null, "42");
            assertThatThrownBy(() -> tournament.transitionTo(target))
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining(target.name());
        }

        @ParameterizedTest
        @EnumSource(value = TournamentStatus.class, names = {"DRAFT", "ACTIVE", "CANCELLED"})
        void shouldRejectTransitionFromFinished(TournamentStatus target) {
            var tournament = finishedTournament();
            assertThatThrownBy(() -> tournament.transitionTo(target))
                    .hasMessageContaining("FINISHED");
        }

        @ParameterizedTest
        @EnumSource(value = TournamentStatus.class, names = {"DRAFT", "ACTIVE", "FINISHED"})
        void shouldRejectTransitionFromCancelled(TournamentStatus target) {
            var tournament = cancelledTournament();
            assertThatThrownBy(() -> tournament.transitionTo(target))
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        void shouldRejectTransitionToSameStatus() {
            var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(1), null, "42");
            assertThatThrownBy(() -> tournament.transitionTo(TournamentStatus.DRAFT))
                    .hasMessageContaining("already DRAFT");
        }
    }

    @Nested
    class ActivationDateValidation {

        @Test
        void shouldRejectActivationWhenStartDateInPast() {
            var tournament = Tournament.create("Copa", null, LocalDate.now().minusDays(1), null, "42");
            assertThatThrownBy(() -> tournament.transitionTo(TournamentStatus.ACTIVE))
                    .hasMessageContaining("startDate must be in the future");
        }

        @Test
        void shouldRejectActivationWhenStartDateIsNull() {
            var tournament = Tournament.create("Copa", null, null, null, "42");
            assertThatThrownBy(() -> tournament.transitionTo(TournamentStatus.ACTIVE))
                    .hasMessageContaining("startDate is required");
        }

        @Test
        void shouldRejectActivationWhenEndDateBeforeStartDate() {
            var tournament = Tournament.create("Copa", null,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 5), "42");
            assertThatThrownBy(() -> tournament.transitionTo(TournamentStatus.ACTIVE))
                    .hasMessageContaining("endDate");
        }

        @Test
        void shouldAllowActivationWithEndDateEqualToStartDate() {
            var date = LocalDate.now().plusDays(7);
            var tournament = Tournament.create("Copa", null, date, date, "42");
            tournament.transitionTo(TournamentStatus.ACTIVE);
            assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.ACTIVE);
        }
    }

    @Nested
    class FieldEditability {

        @Test
        void shouldAllowFullEditInDraft() {
            var tournament = Tournament.create("Old", "Old desc",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "42");
            tournament.update("New", "New desc", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));

            assertThat(tournament.getName()).isEqualTo("New");
            assertThat(tournament.getDescription()).isEqualTo("New desc");
            assertThat(tournament.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(tournament.getEndDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        }

        @Test
        void shouldOnlyUpdateNameAndDescriptionInActive() {
            var tournament = activeTournament();
            var originalStartDate = tournament.getStartDate();
            var originalEndDate = tournament.getEndDate();

            tournament.update("New Name", "New Desc", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1));

            assertThat(tournament.getName()).isEqualTo("New Name");
            assertThat(tournament.getDescription()).isEqualTo("New Desc");
            assertThat(tournament.getStartDate()).isEqualTo(originalStartDate);
            assertThat(tournament.getEndDate()).isEqualTo(originalEndDate);
        }

        @Test
        void shouldRejectUpdateOnFinished() {
            var tournament = finishedTournament();
            assertThatThrownBy(() -> tournament.update("N", "D", null, null))
                    .hasMessageContaining("FINISHED");
        }

        @Test
        void shouldRejectUpdateOnCancelled() {
            var tournament = cancelledTournament();
            assertThatThrownBy(() -> tournament.update("N", "D", null, null))
                    .hasMessageContaining("CANCELLED");
        }
    }

    @Nested
    class DeletionGuard {

        @Test
        void shouldAllowDeletionInDraft() {
            var tournament = Tournament.create("Copa", null, null, null, "42");
            assertThat(tournament.canBeDeleted()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = TournamentStatus.class, names = {"ACTIVE", "FINISHED", "CANCELLED"})
        void shouldRejectDeletionWhenNotDraft(TournamentStatus status) {
            var tournament = activeTournament();
            if (status != TournamentStatus.ACTIVE) {
                tournament.transitionTo(status);
            }
            assertThat(tournament.canBeDeleted()).isFalse();
        }
    }

    @Test
    void shouldTouchUpdatedAtOnTransition() throws InterruptedException {
        var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(7), null, "42");
        var originalUpdatedAt = tournament.getUpdatedAt();
        Thread.sleep(1);
        tournament.transitionTo(TournamentStatus.ACTIVE);
        assertThat(tournament.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void shouldTouchUpdatedAtOnUpdate() throws InterruptedException {
        var tournament = Tournament.create("Copa", null, null, null, "42");
        var originalUpdatedAt = tournament.getUpdatedAt();
        Thread.sleep(1);
        tournament.update("New", null, null, null);
        assertThat(tournament.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    // -- helpers ---------------------------------------------------------------

    private Tournament activeTournament() {
        var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(7), null, "42");
        tournament.transitionTo(TournamentStatus.ACTIVE);
        return tournament;
    }

    private Tournament finishedTournament() {
        var tournament = activeTournament();
        tournament.transitionTo(TournamentStatus.FINISHED);
        return tournament;
    }

    private Tournament cancelledTournament() {
        var tournament = activeTournament();
        tournament.transitionTo(TournamentStatus.CANCELLED);
        return tournament;
    }
}
