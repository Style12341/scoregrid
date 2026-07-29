package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.port.in.AssignTeamsToGroup;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AssignTeamsToGroupUseCase implements AssignTeamsToGroup {

    private final GroupRepository groupRepository;
    private final GroupTeamRepository groupTeamRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TeamRepository teamRepository;

    public AssignTeamsToGroupUseCase(GroupRepository groupRepository,
                                      GroupTeamRepository groupTeamRepository,
                                      TournamentTeamRepository tournamentTeamRepository,
                                      TeamRepository teamRepository) {
        this.groupRepository = groupRepository;
        this.groupTeamRepository = groupTeamRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    public List<Team> execute(Command command) {
        var group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Group not found: " + command.groupId()));

        Long tournamentId = group.getTournamentId();

        var assigned = new ArrayList<Team>();
        for (Long teamId : command.teamIds()) {
            // Check team is registered in the tournament
            if (!tournamentTeamRepository.existsByTournamentIdAndTeamId(tournamentId, teamId)) {
                throw new DomainException(ErrorKind.UNPROCESSABLE, "NOT_REGISTERED",
                        "Team " + teamId + " not registered in this tournament");
            }

            // Check team is not already in another group in this tournament
            var existingGroup = groupTeamRepository
                    .findGroupIdByTeamIdAndTournamentId(teamId, tournamentId);
            if (existingGroup.isPresent() && !existingGroup.get().equals(command.groupId())) {
                throw new DomainException(ErrorKind.CONFLICT, "ALREADY_IN_GROUP",
                        "Team " + teamId + " already belongs to group " + existingGroup.get()
                        + " in this tournament");
            }

            // Idempotent: skip if already in this group
            if (groupTeamRepository.existsByGroupIdAndTeamId(command.groupId(), teamId)) {
                continue;
            }

            groupTeamRepository.assign(command.groupId(), teamId);
        }

        // Return all teams currently assigned to the group
        return groupTeamRepository.findTeamsByGroupId(command.groupId());
    }
}
