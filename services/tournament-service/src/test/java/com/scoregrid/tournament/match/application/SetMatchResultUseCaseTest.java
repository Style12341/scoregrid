package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.in.SetMatchResult;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetMatchResultUseCaseTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchEventPublisher eventPublisher;
    @Mock
    private TournamentRepository tournamentRepository;

    private SetMatchResultUseCase useCase;

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-08-14T18:30:00Z");

    private final TeamRef home = TeamRef.of(7L, "Argentina", "ARG");
    private final TeamRef away = TeamRef.of(8L, "Brazil", "BRA");

    @BeforeEach
    void setUp() {
        useCase = new SetMatchResultUseCase(matchRepository, eventPublisher, tournamentRepository);
        var tournament = Tournament.create("Copa", null, LocalDate.now().plusDays(1), null, "42");
        tournament.transitionTo(TournamentStatus.ACTIVE);
        lenient().when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
    }

    @Test
    void shouldLoadResultHomeWin() {
        var match = Match.create(1L, 3L, null, home, away, FUTURE, NOW);
        match.setId(99L);
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new SetMatchResult.Command(99L, 2, 1);
        useCase.execute(cmd);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(match.getHomeScore()).isEqualTo(2);
        assertThat(match.getAwayScore()).isEqualTo(1);
        assertThat(match.outcome()).isEqualTo("HOME_WIN");
        verify(eventPublisher).finished(match);
    }

    @Test
    void shouldRejectResultOnPostponed() {
        var match = Match.create(1L, 3L, null, home, away, FUTURE, NOW);
        match.setId(99L);
        match.postpone();
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));

        var cmd = new SetMatchResult.Command(99L, 2, 1);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).errorCode()).isEqualTo("INVALID_MATCH_STATE");
                    assertThat(((DomainException) e).getMessage()).contains("postponed");
                });
    }

    @Test
    void shouldRejectResultOnCancelled() {
        var match = Match.create(1L, 3L, null, home, away, FUTURE, NOW);
        match.setId(99L);
        match.cancel();
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));

        var cmd = new SetMatchResult.Command(99L, 2, 1);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(((DomainException) e).getMessage()).contains("cancelled");
                });
    }

    @Test
    void shouldRejectNegativeScore() {
        var cmd = new SetMatchResult.Command(99L, -1, 1);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.VALIDATION));
    }

    @Test
    void shouldRejectScoreOver99() {
        var cmd = new SetMatchResult.Command(99L, 100, 1);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.VALIDATION));
    }

    @Test
    void shouldResubmitResult() {
        var match = Match.create(1L, 3L, null, home, away, FUTURE, NOW);
        match.setId(99L);
        match.loadResult(2, 1); // already finished
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new SetMatchResult.Command(99L, 3, 1);
        useCase.execute(cmd);

        assertThat(match.getHomeScore()).isEqualTo(3);
        verify(eventPublisher).finished(match);
    }
}
