package com.scoregrid.prediction.shared.error;

/**
 * Classification of a domain failure. Deliberately framework-free so domain
 * code can throw without importing Spring; GlobalExceptionHandler maps each
 * kind to an HTTP status.
 */
public enum ErrorKind {
    VALIDATION,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE,
    DOWNSTREAM_UNAVAILABLE
}
