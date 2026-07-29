package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.in.CreateGroup;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateGroupUseCaseTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private GroupRepository groupRepository;

    private CreateGroupUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateGroupUseCase(tournamentRepository, groupRepository);
    }

    @Test
    void shouldCreateGroupSuccessfully() {
        when(tournamentRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.save(any())).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(10L);
            return g;
        });

        var cmd = new CreateGroup.Command(1L, "Grupo A", 0);
        var result = useCase.execute(cmd);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Grupo A");
        assertThat(result.getTournamentId()).isEqualTo(1L);
        assertThat(result.getDisplayOrder()).isEqualTo(0);
        verify(groupRepository).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenTournamentMissing() {
        when(tournamentRepository.existsById(999L)).thenReturn(false);

        var cmd = new CreateGroup.Command(999L, "Grupo X", 0);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }
}
