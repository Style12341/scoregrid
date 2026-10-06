package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.UpdateTournament;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTournamentUseCaseTest {

    @Mock
    private TournamentRepository tournamentRepository;

    private UpdateTournamentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTournamentUseCase(tournamentRepository);
    }

    @ParameterizedTest
    @EnumSource(value = TournamentStatus.class, names = {"FINISHED", "CANCELLED"})
    void shouldRejectUpdatingATerminalTournamentWithConflict(TournamentStatus terminal) {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournamentIn(terminal)));

        var command = new UpdateTournament.Command(1L, "Otro nombre", null, null, null);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(e.errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE");
                    assertThat(e.getMessage()).contains(terminal.name());
                });
        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void shouldStillUpdateAnActiveTournamentsName() {
        var active = tournamentIn(TournamentStatus.ACTIVE);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(active));
        when(tournamentRepository.save(active)).thenReturn(active);

        var result = useCase.execute(new UpdateTournament.Command(1L, "Otro nombre", null, null, null));

        assertThat(result.getName()).isEqualTo("Otro nombre");
    }

    private static Tournament tournamentIn(TournamentStatus status) {
        return Tournament.reconstitute(1L, "Copa", "Desc", status,
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(30), "42",
                Instant.now(), Instant.now());
    }
}
