package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.JoinTournament;
import com.scoregrid.tournament.tournament.domain.port.out.ParticipantRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JoinTournamentUseCaseTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private ParticipantRepository participantRepository;

    private JoinTournamentUseCase useCase;
    private Tournament activeTournament;

    @BeforeEach
    void setUp() {
        useCase = new JoinTournamentUseCase(tournamentRepository, participantRepository);
        activeTournament = Tournament.reconstitute(1L, "Copa 2026", "Desc",
                TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", Instant.now(), Instant.now());
    }

    @Test
    void shouldThrowNotFoundWhenTournamentDoesNotExist() {
        when(tournamentRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new JoinTournament.Command(999L, "42");
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND);
                });
    }

    @Test
    void shouldThrowConflictWhenTournamentIsNotActive() {
        var draft = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.DRAFT, null, null,
                "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var command = new JoinTournament.Command(1L, "42");
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                });
    }

    @Test
    void shouldThrowConflictWhenAlreadyEnrolled() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(participantRepository.exists(1L, "42")).thenReturn(true);

        var command = new JoinTournament.Command(1L, "42");
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                });
    }

    @Test
    void shouldJoinSuccessfully() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(participantRepository.exists(1L, "42")).thenReturn(false);
        when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new JoinTournament.Command(1L, "42");
        var result = useCase.execute(command);

        assertThat(result.getTournamentId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo("42");
        assertThat(result.getJoinedAt()).isNotNull();
        verify(participantRepository).save(any());
    }
}
