package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.in.AssignTeamsToGroup;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignTeamsToGroupUseCaseTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupTeamRepository groupTeamRepository;

    @Mock
    private TournamentTeamRepository tournamentTeamRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TournamentRepository tournamentRepository;

    private AssignTeamsToGroupUseCase useCase;
    private Group group;
    private Team argTeam;
    private Team braTeam;

    @BeforeEach
    void setUp() {
        useCase = new AssignTeamsToGroupUseCase(groupRepository, groupTeamRepository,
                tournamentTeamRepository, teamRepository, tournamentRepository);
        lenient().when(tournamentRepository.findById(1L))
                .thenReturn(Optional.of(Tournament.create("Copa", null, null, null, "42")));

        group = Group.reconstitute(10L, 1L, "Grupo A", 0);

        argTeam = Team.create("Argentina", "ARG", "AR", null);
        argTeam.setId(7L);

        braTeam = Team.create("Brazil", "BRA", "BR", null);
        braTeam.setId(8L);
    }

    @Test
    void shouldAssignTeamsToGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 8L)).thenReturn(true);
        when(groupTeamRepository.findGroupIdByTeamIdAndTournamentId(7L, 1L)).thenReturn(Optional.empty());
        when(groupTeamRepository.findGroupIdByTeamIdAndTournamentId(8L, 1L)).thenReturn(Optional.empty());
        when(groupTeamRepository.existsByGroupIdAndTeamId(10L, 7L)).thenReturn(false);
        when(groupTeamRepository.existsByGroupIdAndTeamId(10L, 8L)).thenReturn(false);
        when(groupTeamRepository.findTeamsByGroupId(10L)).thenReturn(List.of(argTeam, braTeam));

        var cmd = new AssignTeamsToGroup.Command(10L, List.of(7L, 8L));
        var result = useCase.execute(cmd);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Argentina");
        assertThat(result.get(1).getName()).isEqualTo("Brazil");
        verify(groupTeamRepository).assign(10L, 7L);
        verify(groupTeamRepository).assign(10L, 8L);
    }

    @Test
    void shouldBeIdempotentWhenTeamAlreadyInGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(groupTeamRepository.findGroupIdByTeamIdAndTournamentId(7L, 1L)).thenReturn(Optional.of(10L));
        when(groupTeamRepository.existsByGroupIdAndTeamId(10L, 7L)).thenReturn(true);
        when(groupTeamRepository.findTeamsByGroupId(10L)).thenReturn(List.of(argTeam));

        var cmd = new AssignTeamsToGroup.Command(10L, List.of(7L));
        var result = useCase.execute(cmd);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Argentina");
        // assign should NOT be called for the already-assigned team
    }

    @Test
    void shouldThrowConflictWhenTeamAlreadyInAnotherGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(true);
        when(groupTeamRepository.findGroupIdByTeamIdAndTournamentId(7L, 1L)).thenReturn(Optional.of(11L));

        var cmd = new AssignTeamsToGroup.Command(10L, List.of(7L));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT);
                     assertThat(((DomainException) e).errorCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void shouldThrowNotFoundWhenGroupMissing() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());

        var cmd = new AssignTeamsToGroup.Command(999L, List.of(7L));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void shouldThrowUnprocessableWhenTeamNotRegisteredInTournament() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(tournamentTeamRepository.existsByTournamentIdAndTeamId(1L, 7L)).thenReturn(false);

        var cmd = new AssignTeamsToGroup.Command(10L, List.of(7L));
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
                     assertThat(((DomainException) e).errorCode()).isEqualTo("VALIDATION_FAILED");
                });
    }
}
