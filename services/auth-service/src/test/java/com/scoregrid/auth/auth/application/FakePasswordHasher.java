package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.port.out.PasswordHasher;

/**
 * Reversible "hashing" so tests can assert the raw password never survives.
 * Counts equalising calls so the timing defence can be asserted rather than
 * assumed.
 */
class FakePasswordHasher implements PasswordHasher {

    private static final String PREFIX = "hashed:";

    private int burnCount;

    @Override
    public String hash(String rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return hash(rawPassword).equals(passwordHash);
    }

    @Override
    public void burnComparableTime() {
        burnCount++;
    }

    int burnCount() {
        return burnCount;
    }

    static String hashOf(String rawPassword) {
        return PREFIX + rawPassword;
    }
}
