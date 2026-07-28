package com.scoregrid.tournament.team.infrastructure.web;

import com.scoregrid.tournament.team.domain.port.in.AssignTeamsToTournament;
import com.scoregrid.tournament.team.domain.port.in.CreateTeam;
import com.scoregrid.tournament.team.domain.port.in.GetTeam;
import com.scoregrid.tournament.team.domain.port.in.GetTournamentTeams;
import com.scoregrid.tournament.team.domain.port.in.ListTeams;
import com.scoregrid.tournament.team.domain.port.in.UpdateTeam;
import com.scoregrid.tournament.team.infrastructure.web.dto.AssignTeamsRequest;
import com.scoregrid.tournament.team.infrastructure.web.dto.CreateTeamRequest;
import com.scoregrid.tournament.team.infrastructure.web.dto.TeamResponse;
import com.scoregrid.tournament.team.infrastructure.web.dto.UpdateTeamRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
class TeamController {

    private final CreateTeam createTeam;
    private final GetTeam getTeam;
    private final ListTeams listTeams;
    private final UpdateTeam updateTeam;
    private final AssignTeamsToTournament assignTeamsToTournament;
    private final GetTournamentTeams getTournamentTeams;

    TeamController(CreateTeam createTeam, GetTeam getTeam, ListTeams listTeams,
                   UpdateTeam updateTeam, AssignTeamsToTournament assignTeamsToTournament,
                   GetTournamentTeams getTournamentTeams) {
        this.createTeam = createTeam;
        this.getTeam = getTeam;
        this.listTeams = listTeams;
        this.updateTeam = updateTeam;
        this.assignTeamsToTournament = assignTeamsToTournament;
        this.getTournamentTeams = getTournamentTeams;
    }

    @PostMapping("/api/teams")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest request) {
        var command = new CreateTeam.Command(request.name(), request.shortName(),
                request.country(), request.logoUrl());
        var team = createTeam.execute(command);
        var response = TeamResponse.from(team);
        return ResponseEntity.created(URI.create("/api/teams/" + response.id())).body(response);
    }

    @GetMapping("/api/teams")
    ResponseEntity<List<TeamResponse>> list() {
        var teams = listTeams.execute();
        var response = teams.stream().map(TeamResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/teams/{id}")
    ResponseEntity<TeamResponse> get(@PathVariable Long id) {
        var team = getTeam.execute(id);
        return ResponseEntity.ok(TeamResponse.from(team));
    }

    @PutMapping("/api/teams/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TeamResponse> update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateTeamRequest request) {
        var command = new UpdateTeam.Command(id, request.name(), request.shortName(),
                request.country(), request.logoUrl());
        var team = updateTeam.execute(command);
        return ResponseEntity.ok(TeamResponse.from(team));
    }

    @PostMapping("/api/tournaments/{id}/teams")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<List<TeamResponse>> assignTeams(@PathVariable Long id,
                                                     @Valid @RequestBody AssignTeamsRequest request) {
        var command = new AssignTeamsToTournament.Command(id, request.teamIds());
        var teams = assignTeamsToTournament.execute(command);
        var response = teams.stream().map(TeamResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/tournaments/{id}/teams")
    ResponseEntity<List<TeamResponse>> listTournamentTeams(@PathVariable Long id) {
        var teams = getTournamentTeams.execute(id);
        var response = teams.stream().map(TeamResponse::from).toList();
        return ResponseEntity.ok(response);
    }
}
