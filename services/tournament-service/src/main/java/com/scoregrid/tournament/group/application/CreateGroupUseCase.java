package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.in.CreateGroup;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateGroupUseCase implements CreateGroup {

    private final TournamentRepository tournamentRepository;
    private final GroupRepository groupRepository;

    public CreateGroupUseCase(TournamentRepository tournamentRepository,
                               GroupRepository groupRepository) {
        this.tournamentRepository = tournamentRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public Group execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));
        if (tournament.getStatus() != TournamentStatus.DRAFT
                && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Tournament is not configurable in state " + tournament.getStatus());
        }
        var group = Group.create(command.tournamentId(), command.name(), command.displayOrder());
        return groupRepository.save(group);
    }
}
