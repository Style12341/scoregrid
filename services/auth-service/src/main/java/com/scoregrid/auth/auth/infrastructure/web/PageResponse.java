package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.PageResult;

import java.util.List;
import java.util.function.Function;

record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> toResponse) {
        return new PageResponse<>(
                result.items().stream().map(toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
