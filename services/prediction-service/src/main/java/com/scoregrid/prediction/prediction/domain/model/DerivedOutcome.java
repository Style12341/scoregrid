package com.scoregrid.prediction.prediction.domain.model;

public enum DerivedOutcome {
    HOME_WIN,
    DRAW,
    AWAY_WIN;

    public static DerivedOutcome from(int homeScore, int awayScore) {
        if (homeScore > awayScore) return HOME_WIN;
        if (homeScore < awayScore) return AWAY_WIN;
        return DRAW;
    }
}
