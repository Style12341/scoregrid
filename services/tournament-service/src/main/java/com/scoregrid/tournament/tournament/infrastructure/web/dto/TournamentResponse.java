package com.scoregrid.tournament.tournament.infrastructure.web.dto;

import com.scoregrid.tournament.tournament.domain.model.Tournament;

import java.time.Instant;
import java.time.LocalDate;

public record TournamentResponse(
        String id,
        String name,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static TournamentResponse from(Tournament t) {
        return new TournamentResponse(
                t.getId().toString(),
                t.getName(),
                t.getDescription(),
                t.getStatus().name(),
                t.getStartDate(),
                t.getEndDate(),
                t.getCreatedBy(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
