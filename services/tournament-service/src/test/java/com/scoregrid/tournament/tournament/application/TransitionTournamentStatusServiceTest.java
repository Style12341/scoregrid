package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatusUseCase;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionTournamentStatusServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchEventPublisher matchEventPublisher;

    private TransitionTournamentStatusService useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionTournamentStatusService(
                tournamentRepository, matchRepository, matchEventPublisher);
        lenient().when(matchRepository.findByTournamentId(1L)).thenReturn(List.of());
    }

    @Test
    void shouldActivateDraftTournament() {
        var draft = Tournament.create("Copa", "Desc",
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(30), "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(tournamentRepository.save(draft)).thenReturn(draft);

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.ACTIVE);
        var result = useCase.execute(command);

        assertThat(result.getStatus()).isEqualTo(TournamentStatus.ACTIVE);
        verify(tournamentRepository).save(draft);
    }

    @Test
    void shouldRejectActivationWithPastStartDate() {
        var draft = Tournament.create("Copa", "Desc",
                LocalDate.now().minusDays(1), null, "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.ACTIVE);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void shouldRejectActivationWithMissingStartDate() {
        var draft = Tournament.create("Copa", "Desc", null, null, "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.ACTIVE);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void shouldRejectInvalidStateTransition() {
        var draft = Tournament.create("Copa", "Desc",
                LocalDate.now().plusDays(7), null, "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.FINISHED);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE");
                });
    }

    @Test
    void shouldRejectFinishingWhileMatchesArePending() {
        var active = activeTournament();
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(active));
        when(matchRepository.findByTournamentId(1L)).thenReturn(List.of(
                match(MatchStatus.FINISHED),
                match(MatchStatus.SCHEDULED),
                match(MatchStatus.IN_PROGRESS),
                match(MatchStatus.POSTPONED),
                match(MatchStatus.CANCELLED)));

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.FINISHED);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("INVALID_MATCH_STATE");
                    assertThat(e.getMessage()).contains("3");
                });
        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void shouldFinishWhenEveryMatchIsFinishedOrCancelled() {
        var active = activeTournament();
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(active));
        when(tournamentRepository.save(active)).thenReturn(active);
        when(matchRepository.findByTournamentId(1L)).thenReturn(List.of(
                match(MatchStatus.FINISHED), match(MatchStatus.CANCELLED)));

        var command = new TransitionTournamentStatusUseCase.Command(1L, TournamentStatus.FINISHED);
        var result = useCase.execute(command);

        assertThat(result.getStatus()).isEqualTo(TournamentStatus.FINISHED);
    }

    private static Tournament activeTournament() {
        var now = Instant.parse("2026-08-01T00:00:00Z");
        return Tournament.reconstitute(1L, "Copa", "Desc", TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), null, "42", now, now);
    }

    private static Match match(MatchStatus status) {
        return Match.reconstitute(99L, 1L, 3L, null,
                TeamRef.of(7L, "Argentina", "ARG"), TeamRef.of(8L, "Brazil", "BRA"),
                Instant.parse("2026-08-14T18:30:00Z"), status, null, null);
    }

    @Test
    void shouldReturnNotFoundForMissingTournament() {
        when(tournamentRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new TransitionTournamentStatusUseCase.Command(999L, TournamentStatus.ACTIVE);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND);
                });
    }
}
