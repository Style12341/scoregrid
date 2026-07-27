package com.scoregrid.auth.auth.infrastructure.security;

import com.scoregrid.auth.auth.domain.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasher implements PasswordHasher {

    private static final String DUMMY_PLAINTEXT = "timing-equaliser";

    private final PasswordEncoder passwordEncoder;

    /**
     * A real hash of a value nothing will ever submit, used to spend the same
     * work as a genuine comparison when no account was found.
     *
     * <p>Generated at startup rather than hardcoded: a pasted literal that is
     * not a well-formed BCrypt hash makes {@code matches} return false
     * immediately instead of hashing, which silently removes the timing defence
     * it exists to provide. Costs one hash per application start.
     */
    private final String dummyHash;

    BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(DUMMY_PLAINTEXT);
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void burnComparableTime() {
        passwordEncoder.matches(DUMMY_PLAINTEXT, dummyHash);
    }
}
