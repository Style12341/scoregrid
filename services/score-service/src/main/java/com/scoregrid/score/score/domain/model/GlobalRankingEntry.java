package com.scoregrid.score.score.domain.model;

public record GlobalRankingEntry(
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
) {}
