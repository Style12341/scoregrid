package com.scoregrid.tournament.tournament.domain.model;

import java.time.Instant;

/**
 * Value object representing a player's enrolment in a tournament.
 *
 * <p>Identity is the composite (tournamentId, userId), enforced at the
 * database level by a composite primary key.
 */
public class Participant {

    private final Long tournamentId;
    private final String userId;
    private final Instant joinedAt;

    public Participant(Long tournamentId, String userId, Instant joinedAt) {
        this.tournamentId = tournamentId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public static Participant join(Long tournamentId, String userId) {
        return new Participant(tournamentId, userId, Instant.now());
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
