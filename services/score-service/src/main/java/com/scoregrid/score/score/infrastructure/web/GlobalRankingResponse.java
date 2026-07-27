package com.scoregrid.score.score.infrastructure.web;

record GlobalRankingResponse(
        int position,
        String userId,
        String username,
        int totalPoints,
        int tournamentsPlayed,
        int totalHits,
        int exactHits,
        int predictionsScored,
        double accuracy,
        double averagePointsPerTournament
) {
    static GlobalRankingResponse from(com.scoregrid.score.score.domain.model.GlobalRankingEntry entry) {
        return new GlobalRankingResponse(
                entry.position(), entry.userId(), entry.username(),
                entry.totalPoints(), entry.tournamentsPlayed(),
                entry.totalHits(), entry.exactHits(),
                entry.predictionsScored(), entry.accuracy(),
                entry.averagePointsPerTournament());
    }
}
