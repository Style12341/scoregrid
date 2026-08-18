package com.scoregrid.tournament.group.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AssignTeamsRequest(
        @NotEmpty List<@NotBlank String> teamIds
) {}
