package com.scoregrid.score.score.infrastructure.web;

record TournamentRankingResponse(
        int position,
        String userId,
        String username,
        int points,
        int hits,
        int exactHits,
        int predictionsScored,
        double accuracy
) {
    static TournamentRankingResponse from(com.scoregrid.score.score.domain.model.TournamentRankingEntry entry) {
        return new TournamentRankingResponse(
                entry.position(), entry.userId(), entry.username(),
                entry.points(), entry.hits(), entry.exactHits(),
                entry.predictionsScored(), entry.accuracy());
    }
}
