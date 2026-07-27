package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.port.in.RecalculateUseCase;
import com.scoregrid.score.score.domain.port.out.MatchScoreRepository;
import com.scoregrid.score.score.domain.port.out.PredictionClientPort;
import com.scoregrid.score.score.domain.port.out.ScoreEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class RecalculateService implements RecalculateUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecalculateService.class);

    private final ScoreMatchService scoreMatchService;
    private final MatchScoreRepository matchScoreRepository;
    private final PredictionClientPort predictionClient;
    private final ScoreEventPublisher eventPublisher;

    RecalculateService(ScoreMatchService scoreMatchService,
                       MatchScoreRepository matchScoreRepository,
                       PredictionClientPort predictionClient,
                       ScoreEventPublisher eventPublisher) {
        this.scoreMatchService = scoreMatchService;
        this.matchScoreRepository = matchScoreRepository;
        this.predictionClient = predictionClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void recalculateMatch(String matchId) {
        var existing = matchScoreRepository.findByMatchId(matchId);
        if (existing.isEmpty()) {
            log.warn("No existing score for match {}, nothing to recalculate", matchId);
            return;
        }
        var ms = existing.get();
        scoreMatchService.scoreMatch(
                matchId, ms.tournamentId(), ms.homeScore(), ms.awayScore(), ms.outcome());
        log.info("Recalculated match {}", matchId);
    }

    @Override
    public void recalculateTournament(String tournamentId) {
        var scores = matchScoreRepository.findAllByTournamentId(tournamentId);
        for (var ms : scores) {
            scoreMatchService.scoreMatch(
                    ms.matchId(), ms.tournamentId(), ms.homeScore(), ms.awayScore(), ms.outcome());
        }
        log.info("Recalculated tournament {} — {} matches", tournamentId, scores.size());
    }
}
