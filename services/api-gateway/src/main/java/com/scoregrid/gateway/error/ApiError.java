package com.scoregrid.gateway.error;

import java.time.Instant;

/**
 * The one error shape every ScoreGrid service returns, the gateway included.
 * Contract: docs/contracts.md#error-envelope
 *
 * <p>Copied, not shared: each service owns its own copy of the payload
 * classes (AGENTS.md hard rule 2).
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
