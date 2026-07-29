package com.scoregrid.tournament.group.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        int displayOrder
) {}
