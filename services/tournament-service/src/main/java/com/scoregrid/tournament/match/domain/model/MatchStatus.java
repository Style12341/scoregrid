package com.scoregrid.tournament.match.domain.model;

/**
 * Lifecycle states for a match.
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>SCHEDULED → IN_PROGRESS, FINISHED, POSTPONED, CANCELLED</li>
 *   <li>IN_PROGRESS → FINISHED</li>
 *   <li>POSTPONED → SCHEDULED, CANCELLED</li>
 * </ul>
 *
 * Terminal states: FINISHED, CANCELLED.
 */
public enum MatchStatus {

    SCHEDULED,
    IN_PROGRESS,
    FINISHED,
    POSTPONED,
    CANCELLED;

    /**
     * Whether {@code this} status can transition to {@code target}.
     */
    public boolean canTransitionTo(MatchStatus target) {
        if (this == target) return false;
        return switch (this) {
            case SCHEDULED -> target == IN_PROGRESS || target == FINISHED
                    || target == POSTPONED || target == CANCELLED;
            case IN_PROGRESS -> target == FINISHED;
            case POSTPONED -> target == SCHEDULED || target == CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
    }

    /**
     * Whether this status is terminal — no further transitions allowed.
     */
    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
