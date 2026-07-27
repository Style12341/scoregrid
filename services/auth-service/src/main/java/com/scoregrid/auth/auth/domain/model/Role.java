package com.scoregrid.auth.auth.domain.model;

import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;

/**
 * The closed set of roles a ScoreGrid account can hold.
 *
 * <p>The {@code roles} table could hold anything; this enum is what the system
 * actually understands. A row that does not map here is a data error, not a new
 * feature — see docs/contracts.md#authentication-contract.
 */
public enum Role {

    PLAYER,
    ADMIN;

    public static Role of(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "Unknown role: " + name);
        }
    }
}
