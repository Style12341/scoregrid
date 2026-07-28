package com.scoregrid.tournament.tournament.infrastructure.web.dto;

import com.scoregrid.tournament.tournament.domain.model.Participant;

import java.time.Instant;

public record ParticipantResponse(
        String userId,
        String tournamentId,
        Instant joinedAt
) {
    public static ParticipantResponse from(Participant p) {
        return new ParticipantResponse(
                p.getUserId(),
                p.getTournamentId().toString(),
                p.getJoinedAt());
    }
}
