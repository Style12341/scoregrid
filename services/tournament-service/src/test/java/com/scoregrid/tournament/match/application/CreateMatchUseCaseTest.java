package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.match.domain.port.in.CreateMatch;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.model.PhaseType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateMatchUseCaseTest {

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
    private MatchRepository matchRepository;
    @Mock
    private MatchEventPublisher eventPublisher;

    private CreateMatchUseCase useCase;
    private Tournament activeTournament;
    private Team homeTeam;
    private Team awayTeam;

    @BeforeEach
    void setUp() {
        useCase = new CreateMatchUseCase(tournamentRepository, tournamentTeamRepository,
                teamRepository, groupRepository, phaseRepository, groupTeamRepository,
                matchRepository, eventPublisher);

        activeTournament = Tournament.reconstitute(1L, "Copa 2026", "Desc",
                TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", Instant.now(), Instant.now());

        homeTeam = Team.create("Argentina", "ARG", "AR", null);
        homeTeam.setId(7L);

        awayTeam = Team.create("Brazil", "BRA", "BR", null);
        awayTeam.setId(8L);
    }

    @Test
    void shouldThrowNotFoundWhenTournamentMissing() {
        when(tournamentRepository.findById(999L)).thenReturn(Optional.empty());

        var cmd = new CreateMatch.Command(999L, 3L, null, 7L, 8L,
                Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void shouldThrowConflictWhenTournamentIsTerminal() {
        var draft = Tournament.reconstitute(1L, "Copa", "Desc",
                TournamentStatus.CANCELLED, null, null, "42", Instant.now(), Instant.now());
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(draft));

        var cmd = new CreateMatch.Command(1L, 3L, null, 7L, 8L,
                Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode()).isEqualTo("TOURNAMENT_NOT_ACTIVE"));
    }

    @Test
    void shouldThrowUnprocessableWhenTeamNotRegistered() {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(false);

        var cmd = new CreateMatch.Command(1L, 3L, null, 7L, 8L,
                Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.UNPROCESSABLE));
    }

    @Test
    void shouldCreateGroupMatchSuccessfully() {
        var group = Group.reconstitute(3L, 1L, "Grupo A", 0);

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 8L)).thenReturn(true);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 7L)).thenReturn(true);
        when(groupTeamRepository.existsByGroupIdAndTeamId(3L, 8L)).thenReturn(true);
        when(teamRepository.findById(7L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(8L)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any())).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });

        var future = Instant.now().plusSeconds(7200);
        var cmd = new CreateMatch.Command(1L, 3L, null, 7L, 8L, future);
        var result = useCase.execute(cmd);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(result.getGroupId()).isEqualTo(3L);
        assertThat(result.getPhaseId()).isNull();
        verify(eventPublisher).scheduled(any(), any());
    }

    @Test
    void shouldCreatePhaseMatchSuccessfully() {
        var phase = Phase.reconstitute(5L, 1L, "Semifinal", PhaseType.SEMI_FINAL, 4);

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(activeTournament));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 8L)).thenReturn(true);
        when(phaseRepository.findById(5L)).thenReturn(Optional.of(phase));
        when(teamRepository.findById(7L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(8L)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any())).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        var future = Instant.now().plusSeconds(7200);
        var cmd = new CreateMatch.Command(1L, null, 5L, 7L, 8L, future);
        var result = useCase.execute(cmd);

        assertThat(result.getPhaseId()).isEqualTo(5L);
        assertThat(result.getGroupId()).isNull();
        verify(eventPublisher).scheduled(any(), any());
    }
}
