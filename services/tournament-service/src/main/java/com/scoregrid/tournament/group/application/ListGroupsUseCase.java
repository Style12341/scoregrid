package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.in.ListGroups;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListGroupsUseCase implements ListGroups {

    private final TournamentRepository tournamentRepository;
    private final GroupRepository groupRepository;

    public ListGroupsUseCase(TournamentRepository tournamentRepository,
                              GroupRepository groupRepository) {
        this.tournamentRepository = tournamentRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public List<Group> execute(Long tournamentId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + tournamentId);
        }
        return groupRepository.findByTournamentId(tournamentId);
    }
}
