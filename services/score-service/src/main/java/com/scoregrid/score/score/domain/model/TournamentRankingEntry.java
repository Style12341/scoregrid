package com.scoregrid.score.score.domain.model;

public record TournamentRankingEntry(
        int position,
        String userId,
        String username,
        int points,
        int hits,
        int exactHits,
        int predictionsScored,
        double accuracy
) {}
