package com.scoregrid.tournament.group.infrastructure.web;

import com.scoregrid.tournament.group.domain.port.in.AssignTeamsToGroup;
import com.scoregrid.tournament.group.domain.port.in.CreateGroup;
import com.scoregrid.tournament.group.domain.port.in.GetGroupTeams;
import com.scoregrid.tournament.group.domain.port.in.ListGroups;
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

    private final CreateGroup createGroup;
    private final ListGroups listGroups;
    private final AssignTeamsToGroup assignTeamsToGroup;
    private final GetGroupTeams getGroupTeams;

    GroupController(CreateGroup createGroup, ListGroups listGroups,
                    AssignTeamsToGroup assignTeamsToGroup, GetGroupTeams getGroupTeams) {
        this.createGroup = createGroup;
        this.listGroups = listGroups;
        this.assignTeamsToGroup = assignTeamsToGroup;
        this.getGroupTeams = getGroupTeams;
    }

    @PostMapping("/api/tournaments/{id}/groups")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<GroupResponse> create(@PathVariable Long id,
                                          @Valid @RequestBody CreateGroupRequest request) {
        var cmd = new CreateGroup.Command(id, request.name(), request.displayOrder());
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
        var teamIds = request.teamIds().stream().map(Long::valueOf).toList();
        var cmd = new AssignTeamsToGroup.Command(groupId, teamIds);
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
}
