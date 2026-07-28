package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionTournamentStatusUseCaseTest {

    @Mock
    private TournamentRepository tournamentRepository;

    private TransitionTournamentStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionTournamentStatusUseCase(tournamentRepository);
    }

    @Test
    void shouldActivateDraftTournament() {
        var draft = Tournament.create("Copa", "Desc",
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(30), "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(tournamentRepository.save(draft)).thenReturn(draft);

        var command = new TransitionTournamentStatus.Command(1L, TournamentStatus.ACTIVE);
        var result = useCase.execute(command);

        assertThat(result.getStatus()).isEqualTo(TournamentStatus.ACTIVE);
        verify(tournamentRepository).save(draft);
    }

    @Test
    void shouldRejectActivationWithPastStartDate() {
        var draft = Tournament.create("Copa", "Desc",
                LocalDate.now().minusDays(1), null, "42");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var command = new TransitionTournamentStatus.Command(1L, TournamentStatus.ACTIVE);
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

        var command = new TransitionTournamentStatus.Command(1L, TournamentStatus.ACTIVE);
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

        var command = new TransitionTournamentStatus.Command(1L, TournamentStatus.FINISHED);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE");
                });
    }

    @Test
    void shouldReturnNotFoundForMissingTournament() {
        when(tournamentRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new TransitionTournamentStatus.Command(999L, TournamentStatus.ACTIVE);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND);
                });
    }
}
