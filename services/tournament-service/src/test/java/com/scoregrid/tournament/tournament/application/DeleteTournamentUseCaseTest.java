package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
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
class DeleteTournamentUseCaseTest {

    @Mock
    private TournamentRepository tournamentRepository;

    private DeleteTournamentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteTournamentUseCase(tournamentRepository);
    }

    @Test
    void shouldDeleteDraftTournament() {
        var draft = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.DRAFT, null, null,
                "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        useCase.execute(1L);

        verify(tournamentRepository).delete(1L);
    }

    @Test
    void shouldRejectDeletingActiveTournament() {
        var active = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.execute(1L))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE");
                });
    }

    @Test
    void shouldRejectDeletingFinishedTournamentWithConflictCode() {
        var finished = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.FINISHED,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 15),
                "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(finished));

        assertThatThrownBy(() -> useCase.execute(1L))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("CONFLICT");
                });
    }

    @Test
    void shouldRejectDeletingCancelledTournamentWithConflictCode() {
        var cancelled = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.CANCELLED,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 15),
                "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> useCase.execute(1L))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("CONFLICT");
                });
    }
}
