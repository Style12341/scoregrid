package com.scoregrid.tournament.group.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AssignTeamsRequest(
        @NotEmpty List<String> teamIds
) {}
