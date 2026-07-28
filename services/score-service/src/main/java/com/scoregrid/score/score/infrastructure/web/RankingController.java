package com.scoregrid.score.score.infrastructure.web;

import com.scoregrid.score.score.domain.port.in.GetRankingsUseCase;
import com.scoregrid.score.score.domain.port.in.RecalculateUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
class RankingController {

    private final GetRankingsUseCase getRankings;
    private final RecalculateUseCase recalculate;

    RankingController(GetRankingsUseCase getRankings, RecalculateUseCase recalculate) {
        this.getRankings = getRankings;
        this.recalculate = recalculate;
    }

    @GetMapping("/tournament/{tournamentId}")
    ResponseEntity<List<TournamentRankingResponse>> getTournamentRanking(
            @PathVariable String tournamentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var entries = getRankings.getTournamentRanking(tournamentId, page, size);
        return ResponseEntity.ok(entries.stream().map(TournamentRankingResponse::from).toList());
    }

    @GetMapping("/global")
    ResponseEntity<List<GlobalRankingResponse>> getGlobalRanking(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var entries = getRankings.getGlobalRanking(page, size);
        return ResponseEntity.ok(entries.stream().map(GlobalRankingResponse::from).toList());
    }

    @GetMapping("/user/{userId}")
    ResponseEntity<List<TournamentRankingResponse>> getUserRanking(@PathVariable String userId) {
        var entries = getRankings.getUserRanking(userId);
        return ResponseEntity.ok(entries.stream().map(TournamentRankingResponse::from).toList());
    }

    @PostMapping("/recalculate/match/{matchId}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> recalculateMatch(@PathVariable String matchId) {
        recalculate.recalculateMatch(matchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recalculate/tournament/{tournamentId}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> recalculateTournament(@PathVariable String tournamentId) {
        recalculate.recalculateTournament(tournamentId);
        return ResponseEntity.noContent().build();
    }
}
