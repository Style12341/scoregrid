package com.scoregrid.tournament.team.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 10) String shortName,
        @Size(max = 5) String country,
        String logoUrl
) {}
