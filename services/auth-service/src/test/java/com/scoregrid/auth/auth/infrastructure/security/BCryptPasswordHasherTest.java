package com.scoregrid.auth.auth.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private PasswordEncoder passwordEncoder;
    private BCryptPasswordHasher hasher;

    @BeforeEach
    void setUp() {
        // Lowest valid strength: these tests are about behaviour, not work factor,
        // and the default (10) makes them needlessly slow.
        passwordEncoder = new BCryptPasswordEncoder(4);
        hasher = new BCryptPasswordHasher(passwordEncoder);
    }

    @Test
    void hashesToSomethingOtherThanTheInput() {
        String hash = hasher.hash("correct-horse");

        assertThat(hash).isNotEqualTo("correct-horse").startsWith("$2");
    }

    @Test
    @DisplayName("the same password hashes differently each time — BCrypt salts")
    void hashesAreSalted() {
        assertThat(hasher.hash("correct-horse")).isNotEqualTo(hasher.hash("correct-horse"));
    }

    @Test
    void matchesTheOriginalPassword() {
        String hash = hasher.hash("correct-horse");

        assertThat(hasher.matches("correct-horse", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    @DisplayName("burnComparableTime does real BCrypt work, not a no-op")
    void timingEqualiserActuallyHashes() {
        // A malformed dummy hash would make matches() bail out immediately and
        // silently remove the defence. Comparing against a real hash of the
        // same cost proves work is being done: a no-op would be orders of
        // magnitude faster than this bound, and a real hash comfortably inside it.
        long realStart = System.nanoTime();
        passwordEncoder.matches("anything", passwordEncoder.encode("something-else"));
        long realCost = System.nanoTime() - realStart;

        long burnStart = System.nanoTime();
        hasher.burnComparableTime();
        long burnCost = System.nanoTime() - burnStart;

        assertThat(burnCost)
                .as("burnComparableTime should cost a comparable order of magnitude to a real match")
                .isGreaterThan(realCost / 20);
    }

    @Test
    @DisplayName("burnComparableTime is safe to call repeatedly and never throws")
    void timingEqualiserIsRepeatable() {
        for (int i = 0; i < 3; i++) {
            hasher.burnComparableTime();
        }
    }
}
