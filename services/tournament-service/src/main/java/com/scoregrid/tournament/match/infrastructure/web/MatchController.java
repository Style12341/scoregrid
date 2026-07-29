package com.scoregrid.tournament.match.infrastructure.web;

import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.port.in.CreateMatch;
import com.scoregrid.tournament.match.domain.port.in.GetMatch;
import com.scoregrid.tournament.match.domain.port.in.ListMatches;
import com.scoregrid.tournament.match.domain.port.in.SetMatchResult;
import com.scoregrid.tournament.match.domain.port.in.UpdateMatch;
import com.scoregrid.tournament.match.infrastructure.web.dto.CreateMatchRequest;
import com.scoregrid.tournament.match.infrastructure.web.dto.MatchResponse;
import com.scoregrid.tournament.match.infrastructure.web.dto.SetMatchResultRequest;
import com.scoregrid.tournament.match.infrastructure.web.dto.UpdateMatchRequest;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
class MatchController {

    private final CreateMatch createMatch;
    private final GetMatch getMatch;
    private final ListMatches listMatches;
    private final UpdateMatch updateMatch;
    private final SetMatchResult setMatchResult;

    MatchController(CreateMatch createMatch, GetMatch getMatch,
                    ListMatches listMatches, UpdateMatch updateMatch,
                    SetMatchResult setMatchResult) {
        this.createMatch = createMatch;
        this.getMatch = getMatch;
        this.listMatches = listMatches;
        this.updateMatch = updateMatch;
        this.setMatchResult = setMatchResult;
    }

    @PostMapping("/api/tournaments/{id}/matches")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<MatchResponse> create(@PathVariable Long id,
                                          @Valid @RequestBody CreateMatchRequest request) {
        Long groupId = request.groupId() != null ? Long.valueOf(request.groupId()) : null;
        Long phaseId = request.phaseId() != null ? Long.valueOf(request.phaseId()) : null;

        var cmd = new CreateMatch.Command(id, groupId, phaseId,
                Long.valueOf(request.homeTeamId()), Long.valueOf(request.awayTeamId()),
                request.startTime());
        var match = createMatch.execute(cmd);
        var response = MatchResponse.from(match);
        return ResponseEntity.created(URI.create("/api/matches/" + response.id())).body(response);
    }

    @GetMapping("/api/matches/{id}")
    ResponseEntity<MatchResponse> get(@PathVariable Long id) {
        var match = getMatch.execute(id);
        return ResponseEntity.ok(MatchResponse.from(match));
    }

    @GetMapping("/api/tournaments/{id}/matches")
    ResponseEntity<List<MatchResponse>> list(@PathVariable Long id,
                                              @RequestParam(required = false) String status) {
        Optional<MatchStatus> statusFilter = Optional.empty();
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = Optional.of(MatchStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                        "Invalid status value: " + status);
            }
        }
        var matches = listMatches.execute(id, statusFilter);
        var response = matches.stream().map(MatchResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/matches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<MatchResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateMatchRequest request) {
        Long groupId = request.groupId() != null ? Long.valueOf(request.groupId()) : null;
        Long phaseId = request.phaseId() != null ? Long.valueOf(request.phaseId()) : null;

        var cmd = new UpdateMatch.Command(id, groupId, phaseId,
                Long.valueOf(request.homeTeamId()), Long.valueOf(request.awayTeamId()),
                request.startTime(), request.status());
        var match = updateMatch.execute(cmd);
        return ResponseEntity.ok(MatchResponse.from(match));
    }

    @PutMapping("/api/matches/{id}/result")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> setResult(@PathVariable Long id,
                                    @Valid @RequestBody SetMatchResultRequest request) {
        var cmd = new SetMatchResult.Command(id, request.homeScore(), request.awayScore());
        setMatchResult.execute(cmd);
        return ResponseEntity.noContent().build();
    }
}
