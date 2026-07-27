package com.scoregrid.tournament.shared.error;

/**
 * Base class for business rule violations.
 *
 * <p>Never throw raw RuntimeException for a rule violation: the error code is
 * part of the API contract (see docs/contracts.md#error-envelope) and callers
 * branch on it.
 */
public class DomainException extends RuntimeException {

    private final ErrorKind kind;
    private final String errorCode;

    public DomainException(ErrorKind kind, String errorCode, String message) {
        super(message);
        this.kind = kind;
        this.errorCode = errorCode;
    }

    public ErrorKind kind() {
        return kind;
    }

    public String errorCode() {
        return errorCode;
    }
}
