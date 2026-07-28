package com.scoregrid.tournament.tournament.infrastructure.web;

import com.scoregrid.tournament.shared.security.CurrentUser;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.CreateTournament;
import com.scoregrid.tournament.tournament.domain.port.in.DeleteTournament;
import com.scoregrid.tournament.tournament.domain.port.in.GetParticipant;
import com.scoregrid.tournament.tournament.domain.port.in.GetTournament;
import com.scoregrid.tournament.tournament.domain.port.in.JoinTournament;
import com.scoregrid.tournament.tournament.domain.port.in.ListParticipants;
import com.scoregrid.tournament.tournament.domain.port.in.ListTournaments;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.UpdateTournament;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.CreateTournamentRequest;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.PagedResponse;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.ParticipantResponse;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.StatusTransitionRequest;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.TournamentResponse;
import com.scoregrid.tournament.tournament.infrastructure.web.dto.UpdateTournamentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/tournaments")
class TournamentController {

    private final CreateTournament createTournament;
    private final GetTournament getTournament;
    private final ListTournaments listTournaments;
    private final UpdateTournament updateTournament;
    private final TransitionTournamentStatus transitionTournamentStatus;
    private final DeleteTournament deleteTournament;
    private final JoinTournament joinTournament;
    private final ListParticipants listParticipants;
    private final GetParticipant getParticipant;
    private final CurrentUser currentUser;

    TournamentController(CreateTournament createTournament,
                         GetTournament getTournament,
                         ListTournaments listTournaments,
                         UpdateTournament updateTournament,
                         TransitionTournamentStatus transitionTournamentStatus,
                         DeleteTournament deleteTournament,
                         JoinTournament joinTournament,
                         ListParticipants listParticipants,
                         GetParticipant getParticipant,
                         CurrentUser currentUser) {
        this.createTournament = createTournament;
        this.getTournament = getTournament;
        this.listTournaments = listTournaments;
        this.updateTournament = updateTournament;
        this.transitionTournamentStatus = transitionTournamentStatus;
        this.deleteTournament = deleteTournament;
        this.joinTournament = joinTournament;
        this.listParticipants = listParticipants;
        this.getParticipant = getParticipant;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TournamentResponse> create(@Valid @RequestBody CreateTournamentRequest request) {
        var command = new CreateTournament.Command(
                request.name(), request.description(),
                request.startDate(), request.endDate(),
                currentUser.requireId());
        var tournament = createTournament.execute(command);
        var response = TournamentResponse.from(tournament);
        return ResponseEntity.created(URI.create("/api/tournaments/" + response.id())).body(response);
    }

    @GetMapping
    ResponseEntity<PagedResponse<TournamentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Optional<TournamentStatus> statusFilter = Optional.empty();
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = Optional.of(TournamentStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new com.scoregrid.tournament.shared.error.DomainException(
                        com.scoregrid.tournament.shared.error.ErrorKind.VALIDATION,
                        "VALIDATION_FAILED",
                        "Invalid status value: " + status);
            }
        }

        var result = listTournaments.execute(statusFilter, page, size);
        var content = result.content().stream()
                .map(TournamentResponse::from)
                .toList();
        var paged = new PagedResponse<>(content, result.totalElements(),
                result.totalPages(), result.number(), result.size());
        return ResponseEntity.ok(paged);
    }

    @GetMapping("/{id}")
    ResponseEntity<TournamentResponse> get(@PathVariable Long id) {
        var tournament = getTournament.execute(id);
        return ResponseEntity.ok(TournamentResponse.from(tournament));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TournamentResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateTournamentRequest request) {
        var command = new UpdateTournament.Command(
                id, request.name(), request.description(),
                request.startDate(), request.endDate());
        var tournament = updateTournament.execute(command);
        return ResponseEntity.ok(TournamentResponse.from(tournament));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TournamentResponse> transitionStatus(@PathVariable Long id,
                                                         @Valid @RequestBody StatusTransitionRequest request) {
        TournamentStatus targetStatus;
        try {
            targetStatus = TournamentStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.scoregrid.tournament.shared.error.DomainException(
                    com.scoregrid.tournament.shared.error.ErrorKind.VALIDATION,
                    "VALIDATION_FAILED",
                    "Invalid status value: " + request.status());
        }

        var command = new TransitionTournamentStatus.Command(id, targetStatus);
        var tournament = transitionTournamentStatus.execute(command);
        return ResponseEntity.ok(TournamentResponse.from(tournament));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteTournament.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('PLAYER')")
    ResponseEntity<ParticipantResponse> join(@PathVariable Long id) {
        var command = new JoinTournament.Command(id, currentUser.requireId());
        var participant = joinTournament.execute(command);
        return ResponseEntity.ok(ParticipantResponse.from(participant));
    }

    @GetMapping("/{id}/participants")
    ResponseEntity<java.util.List<ParticipantResponse>> listParticipants(@PathVariable Long id) {
        var participants = listParticipants.execute(id);
        var response = participants.stream()
                .map(ParticipantResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Single-participant lookup used by Prediction Service for enrolment verification.
     * Prediction Service interprets 404 as NOT_ENROLLED — if this status code changes,
     * update docs/contracts.md.
     */
    @GetMapping("/{id}/participants/{userId}")
    ResponseEntity<ParticipantResponse> getParticipant(@PathVariable Long id,
                                                        @PathVariable String userId) {
        var participant = getParticipant.execute(id, userId);
        return ResponseEntity.ok(ParticipantResponse.from(participant));
    }
}
