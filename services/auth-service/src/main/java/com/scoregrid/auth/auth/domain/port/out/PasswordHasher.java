package com.scoregrid.auth.auth.domain.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /**
     * Burns roughly the same time as a real {@link #matches} call.
     *
     * <p>Called when no account was found, so that "no such user" and "wrong
     * password" take comparable time. Without it, response latency answers the
     * question the 401 message deliberately refuses to.
     */
    void burnComparableTime();
}
