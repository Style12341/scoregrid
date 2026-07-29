package com.scoregrid.tournament.group.application;

import com.scoregrid.tournament.group.domain.port.in.GetGroupTeams;
import com.scoregrid.tournament.group.domain.port.out.GroupRepository;
import com.scoregrid.tournament.group.domain.port.out.GroupTeamRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.team.domain.model.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetGroupTeamsUseCase implements GetGroupTeams {

    private final GroupRepository groupRepository;
    private final GroupTeamRepository groupTeamRepository;

    public GetGroupTeamsUseCase(GroupRepository groupRepository,
                                 GroupTeamRepository groupTeamRepository) {
        this.groupRepository = groupRepository;
        this.groupTeamRepository = groupTeamRepository;
    }

    @Override
    public List<Team> execute(Long groupId) {
        if (groupRepository.findById(groupId).isEmpty()) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Group not found: " + groupId);
        }
        return groupTeamRepository.findTeamsByGroupId(groupId);
    }
}
