package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.model.Group;
import com.scoregrid.tournament.group.domain.port.in.CreateGroupUseCase;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateGroupService implements CreateGroupUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateGroupService.class);

    private final TournamentRepository tournamentRepository;
    private final GroupRepository groupRepository;

    public CreateGroupService(TournamentRepository tournamentRepository,
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
        var saved = groupRepository.save(group);
        log.info("Group created: id={} tournamentId={} name={}",
                saved.getId(), saved.getTournamentId(), saved.getName());
        return saved;
    }
}
