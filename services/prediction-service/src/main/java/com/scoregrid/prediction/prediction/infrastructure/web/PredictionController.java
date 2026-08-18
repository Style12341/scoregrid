package com.scoregrid.prediction.prediction.infrastructure.web;

import com.scoregrid.prediction.prediction.domain.port.in.CreatePredictionUseCase;
import com.scoregrid.prediction.prediction.domain.port.in.GetPredictionsUseCase;
import com.scoregrid.prediction.prediction.domain.port.in.UpdatePredictionUseCase;
import com.scoregrid.prediction.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/predictions")
class PredictionController {

    private final CreatePredictionUseCase createPrediction;
    private final UpdatePredictionUseCase updatePrediction;
    private final GetPredictionsUseCase getPredictions;
    private final CurrentUser currentUser;

    PredictionController(CreatePredictionUseCase createPrediction,
                         UpdatePredictionUseCase updatePrediction,
                         GetPredictionsUseCase getPredictions,
                         CurrentUser currentUser) {
        this.createPrediction = createPrediction;
        this.updatePrediction = updatePrediction;
        this.getPredictions = getPredictions;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLAYER')")
    ResponseEntity<PredictionResponse> create(@Valid @RequestBody CreatePredictionRequest request) {
        String userId = currentUser.requireId();
        var prediction = createPrediction.create(userId, request.matchId(), request.homeScore(), request.awayScore());
        return ResponseEntity.status(HttpStatus.CREATED).body(PredictionResponse.from(prediction));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLAYER')")
    ResponseEntity<PredictionResponse> update(@PathVariable String id,
                                              @Valid @RequestBody UpdatePredictionRequest request) {
        String userId = currentUser.requireId();
        var prediction = updatePrediction.update(userId, id, request.homeScore(), request.awayScore());
        return ResponseEntity.ok(PredictionResponse.from(prediction));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    ResponseEntity<List<PredictionResponse>> getMine(
            @RequestParam(required = false) String tournamentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = currentUser.requireId();
        var predictions = tournamentId != null
                ? getPredictions.getMyPredictions(userId, tournamentId, page, size)
                : getPredictions.getAllMyPredictions(userId, page, size);
        return ResponseEntity.ok(predictions.stream().map(PredictionResponse::from).toList());
    }

    @GetMapping("/me/match/{matchId}")
    @PreAuthorize("hasRole('PLAYER')")
    ResponseEntity<PredictionResponse> getMineForMatch(@PathVariable String matchId) {
        String userId = currentUser.requireId();
        return getPredictions.getMyPredictionForMatch(userId, matchId)
                .map(prediction -> ResponseEntity.ok(PredictionResponse.from(prediction)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/match/{matchId}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<List<PredictionResponse>> getByMatch(@PathVariable String matchId) {
        var predictions = getPredictions.getPredictionsByMatch(matchId);
        return ResponseEntity.ok(predictions.stream().map(PredictionResponse::from).toList());
    }

    @GetMapping("/user/{userId}/tournament/{tournamentId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    ResponseEntity<List<PredictionResponse>> getByUserAndTournament(@PathVariable String userId,
                                                                     @PathVariable String tournamentId) {
        var predictions = getPredictions.getPredictionsByUserAndTournament(userId, tournamentId);
        return ResponseEntity.ok(predictions.stream().map(PredictionResponse::from).toList());
    }
}
