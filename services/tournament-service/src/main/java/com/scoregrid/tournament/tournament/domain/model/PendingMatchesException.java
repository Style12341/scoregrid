package com.scoregrid.tournament.tournament.domain.model;

/**
 * A tournament cannot finish while any of its matches can still be played or
 * scored (scheduled, in progress or postponed).
 */
public class PendingMatchesException extends IllegalStateException {

    private final long pendingMatches;

    public PendingMatchesException(long pendingMatches) {
        super("Cannot finish tournament: " + pendingMatches
                + " match(es) still scheduled, in progress or postponed");
        this.pendingMatches = pendingMatches;
    }

    public long pendingMatches() {
        return pendingMatches;
    }
}
