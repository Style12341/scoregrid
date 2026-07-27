package com.scoregrid.auth.auth.domain.model;

import java.time.Instant;

/** A signed JWT and the instant it stops being valid. */
public record IssuedToken(String value, Instant expiresAt) {
}
