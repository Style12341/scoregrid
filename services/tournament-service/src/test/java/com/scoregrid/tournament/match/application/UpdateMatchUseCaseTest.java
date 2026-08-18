package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.in.UpdateMatch;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateMatchUseCaseTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentTeamRepository tournamentTeamRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private PhaseRepository phaseRepository;
    @Mock
    private GroupTeamRepository groupTeamRepository;
    @Mock
    private MatchEventPublisher eventPublisher;

    private UpdateMatchUseCase useCase;
    private Tournament activeTournament;
    private Team homeTeam;
    private Team awayTeam;
    private Group group;

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FUTURE = Instant.now().plusSeconds(7200);
    private static final Instant LATER = Instant.now().plusSeconds(10800);
    private static final TeamRef homeRef = TeamRef.of(7L, "Argentina", "ARG");
    private static final TeamRef awayRef = TeamRef.of(8L, "Brazil", "BRA");

    @BeforeEach
    void setUp() {
        useCase = new UpdateMatchUseCase(matchRepository, tournamentRepository,
                tournamentTeamRepository, teamRepository, groupRepository, phaseRepository,
                groupTeamRepository, eventPublisher);

        activeTournament = Tournament.reconstitute(1L, "Copa 2026", "Desc",
                TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", NOW, NOW);

        homeTeam = Team.create("Argentina", "ARG", "AR", null);
        homeTeam.setId(7L);
        awayTeam = Team.create("Brazil", "BRA", "BR", null);
        awayTeam.setId(8L);

        group = Group.reconstitute(3L, 1L, "Grupo A", 0);
    }

    private Match matchWithStatus(MatchStatus status) {
        return Match.reconstitute(99L, 1L, 3L, null,
                homeRef, awayRef, FUTURE, status, null, null);
    }

    private void setupSuccessfulUpdateMocks(Match match) {
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 8L)).thenReturn(true);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 7L)).thenReturn(true);
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 8L)).thenReturn(true);
        when(teamRepository.findById(7L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(8L)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -- Not found / conflict ---------------------------------------------------

    @Test
    void shouldThrowNotFoundWhenMatchMissing() {
        when(matchRepository.findById(999L)).thenReturn(Optional.empty());

        var cmd = new UpdateMatch.Command(999L, 3L, null, 7L, 8L, FUTURE, MatchStatus.SCHEDULED);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void shouldThrowConflictWhenTournamentIsTerminal() {
        var draft = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.CANCELLED, null, null, "42", NOW, NOW);
        var match = matchWithStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, FUTURE, MatchStatus.SCHEDULED);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE"));
    }

    // -- Status transitions -----------------------------------------------------

    @Test
    void shouldTransitionScheduledToPostponed() {
        setupSuccessfulUpdateMocks(matchWithStatus(MatchStatus.SCHEDULED));

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, FUTURE, MatchStatus.POSTPONED);
        var result = useCase.execute(cmd);

        assertThat(result.getStatus()).isEqualTo(MatchStatus.POSTPONED);
        verify(eventPublisher).updated(any(), any());
    }

    @Test
    void shouldTransitionScheduledToCancelled() {
        setupSuccessfulUpdateMocks(matchWithStatus(MatchStatus.SCHEDULED));

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, FUTURE, MatchStatus.CANCELLED);
        var result = useCase.execute(cmd);

        assertThat(result.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        verify(eventPublisher).updated(any(), any());
    }

    @Test
    void shouldTransitionPostponedToCancelled() {
        setupSuccessfulUpdateMocks(matchWithStatus(MatchStatus.POSTPONED));

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, FUTURE, MatchStatus.CANCELLED);
        var result = useCase.execute(cmd);

        assertThat(result.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        verify(eventPublisher).updated(any(), any());
    }

    @Test
    void shouldTransitionPostponedToScheduled() {
        setupSuccessfulUpdateMocks(matchWithStatus(MatchStatus.POSTPONED));

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, LATER, MatchStatus.SCHEDULED);
        var result = useCase.execute(cmd);

        assertThat(result.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(result.getStartTime()).isEqualTo(LATER);
        verify(eventPublisher).updated(any(), any());
    }

    @Test
    void shouldRejectInvalidTransition() {
        var finished = matchWithStatus(MatchStatus.FINISHED);
        when(matchRepository.findById(99L)).thenReturn(Optional.of(finished));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, FUTURE, MatchStatus.POSTPONED);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                 .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT));
    }

    // -- Event publishing -------------------------------------------------------

    @Test
    void shouldPublishEventWhenStartTimeChanges() {
        var match = Match.reconstitute(99L, 1L, 3L, null,
                homeRef, awayRef, FUTURE, MatchStatus.SCHEDULED, null, null);
        setupSuccessfulUpdateMocks(match);

        var cmd = new UpdateMatch.Command(99L, 3L, null, 7L, 8L, LATER, MatchStatus.SCHEDULED);
        var result = useCase.execute(cmd);

        assertThat(result.getStartTime()).isEqualTo(LATER);
        verify(eventPublisher).updated(any(), any());
    }

    @Test
    void shouldNotPublishEventWhenOnlyTeamsChange() {
        var match = matchWithStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 9L)).thenReturn(true);
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 10L)).thenReturn(true);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 9L)).thenReturn(true);
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 10L)).thenReturn(true);

        var newHome = Team.create("Uruguay", "URU", "UY", null);
        newHome.setId(9L);
        var newAway = Team.create("Chile", "CHI", "CL", null);
        newAway.setId(10L);

        when(teamRepository.findById(9L)).thenReturn(Optional.of(newHome));
        when(teamRepository.findById(10L)).thenReturn(Optional.of(newAway));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Same status, same startTime, same group — only teams changed
        var cmd = new UpdateMatch.Command(99L, 3L, null, 9L, 10L, FUTURE, MatchStatus.SCHEDULED);
        useCase.execute(cmd);

        verify(eventPublisher, never()).updated(any(), any());
    }
}
