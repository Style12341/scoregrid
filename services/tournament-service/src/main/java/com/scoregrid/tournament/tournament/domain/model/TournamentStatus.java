package com.scoregrid.tournament.tournament.domain.model;

/**
 * Lifecycle states for a tournament.
 *
 * <p>DRAFT → ACTIVE → FINISHED
 * <br>DRAFT → CANCELLED
 * <br>ACTIVE → CANCELLED
 * <br>All other transitions are invalid.
 */
public enum TournamentStatus {
    DRAFT,
    ACTIVE,
    FINISHED,
    CANCELLED
}
