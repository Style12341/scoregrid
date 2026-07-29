package com.scoregrid.tournament.phase.infrastructure.web;

import com.scoregrid.tournament.phase.domain.port.in.CreatePhase;
import com.scoregrid.tournament.phase.domain.port.in.ListPhases;
import com.scoregrid.tournament.phase.infrastructure.web.dto.CreatePhaseRequest;
import com.scoregrid.tournament.phase.infrastructure.web.dto.PhaseResponse;
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
class PhaseController {

    private final CreatePhase createPhase;
    private final ListPhases listPhases;

    PhaseController(CreatePhase createPhase, ListPhases listPhases) {
        this.createPhase = createPhase;
        this.listPhases = listPhases;
    }

    @PostMapping("/api/tournaments/{id}/phases")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<PhaseResponse> create(@PathVariable Long id,
                                          @Valid @RequestBody CreatePhaseRequest request) {
        var cmd = new CreatePhase.Command(id, request.type(), request.name(), request.displayOrder());
        var phase = createPhase.execute(cmd);
        var response = PhaseResponse.from(phase);
        return ResponseEntity.created(URI.create("/api/phases/" + response.id())).body(response);
    }

    @GetMapping("/api/tournaments/{id}/phases")
    ResponseEntity<List<PhaseResponse>> list(@PathVariable Long id) {
        var phases = listPhases.execute(id);
        var response = phases.stream().map(PhaseResponse::from).toList();
        return ResponseEntity.ok(response);
    }
}
