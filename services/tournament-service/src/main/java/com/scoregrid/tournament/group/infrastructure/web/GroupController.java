package com.scoregrid.tournament.group.infrastructure.web;

import com.scoregrid.tournament.group.domain.port.in.AssignTeamsToGroupUseCase;
import com.scoregrid.tournament.group.domain.port.in.CreateGroupUseCase;
import com.scoregrid.tournament.group.domain.port.in.GetGroupTeamsUseCase;
import com.scoregrid.tournament.group.domain.port.in.ListGroupsUseCase;
import com.scoregrid.tournament.group.infrastructure.web.dto.AssignTeamsRequest;
import com.scoregrid.tournament.group.infrastructure.web.dto.CreateGroupRequest;
import com.scoregrid.tournament.group.infrastructure.web.dto.GroupResponse;
import com.scoregrid.tournament.team.infrastructure.web.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
class GroupController {

    private final CreateGroupUseCase createGroup;
    private final ListGroupsUseCase listGroups;
    private final AssignTeamsToGroupUseCase assignTeamsToGroup;
    private final GetGroupTeamsUseCase getGroupTeams;

    GroupController(CreateGroupUseCase createGroup, ListGroupsUseCase listGroups,
                    AssignTeamsToGroupUseCase assignTeamsToGroup, GetGroupTeamsUseCase getGroupTeams) {
        this.createGroup = createGroup;
        this.listGroups = listGroups;
        this.assignTeamsToGroup = assignTeamsToGroup;
        this.getGroupTeams = getGroupTeams;
    }

    @PostMapping("/api/tournaments/{id}/groups")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<GroupResponse> create(@PathVariable Long id,
                                          @Valid @RequestBody CreateGroupRequest request) {
        var cmd = new CreateGroupUseCase.Command(id, request.name(), request.displayOrder());
        var group = createGroup.execute(cmd);
        var response = GroupResponse.from(group);
        return ResponseEntity.created(URI.create("/api/groups/" + response.id())).body(response);
    }

    @GetMapping("/api/tournaments/{id}/groups")
    ResponseEntity<List<GroupResponse>> list(@PathVariable Long id) {
        var groups = listGroups.execute(id);
        var response = groups.stream().map(GroupResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/groups/{groupId}/teams")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<List<TeamResponse>> assignTeams(@PathVariable Long groupId,
                                                    @Valid @RequestBody AssignTeamsRequest request) {
        var teamIds = request.teamIds().stream().map(value -> parseId(value, "teamId")).toList();
        var cmd = new AssignTeamsToGroupUseCase.Command(groupId, teamIds);
        var teams = assignTeamsToGroup.execute(cmd);
        var response = teams.stream().map(TeamResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/groups/{groupId}/teams")
    ResponseEntity<List<TeamResponse>> listTeams(@PathVariable Long groupId) {
        var teams = getGroupTeams.execute(groupId);
        var response = teams.stream().map(TeamResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    private static Long parseId(String value, String field) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new com.scoregrid.tournament.shared.error.DomainException(
                    com.scoregrid.tournament.shared.error.ErrorKind.VALIDATION,
                    "VALIDATION_FAILED", field + " must be a numeric ID");
        }
    }
}
