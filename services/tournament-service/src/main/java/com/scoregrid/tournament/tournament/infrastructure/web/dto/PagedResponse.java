package com.scoregrid.tournament.tournament.infrastructure.web.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {}
