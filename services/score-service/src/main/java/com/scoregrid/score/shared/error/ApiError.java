package com.scoregrid.score.shared.error;

import java.time.Instant;

/**
 * The one error shape every ScoreGrid service returns.
 * Contract: docs/contracts.md#error-envelope
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(int status, String errorCode, String message, String path) {
        return new ApiError(Instant.now(), status, errorCode, message, path);
    }
}
