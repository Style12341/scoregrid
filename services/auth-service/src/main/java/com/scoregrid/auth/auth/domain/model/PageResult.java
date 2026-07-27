package com.scoregrid.auth.auth.domain.model;

import java.util.List;

/**
 * A page of results, without importing Spring Data into the domain.
 *
 * <p>{@code Page<T>} would be the obvious type and is exactly what the domain
 * must not depend on.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
