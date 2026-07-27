package com.scoregrid.tournament.tournament.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantTest {

    @Test
    void joinShouldCreateParticipantWithCorrectIdentity() {
        var participant = Participant.join(42L, "user-99");

        assertThat(participant.getTournamentId()).isEqualTo(42L);
        assertThat(participant.getUserId()).isEqualTo("user-99");
        assertThat(participant.getJoinedAt()).isNotNull();
    }

    @Test
    void joinShouldSetCurrentTimestamp() {
        var before = java.time.Instant.now().minusSeconds(1);
        var participant = Participant.join(1L, "42");
        var after = java.time.Instant.now().plusSeconds(1);

        assertThat(participant.getJoinedAt()).isBetween(before, after);
    }

    @Test
    void participantShouldBeImmutable() {
        var participant = Participant.join(1L, "42");

        assertThat(participant.getTournamentId()).isEqualTo(1L);
        assertThat(participant.getUserId()).isEqualTo("42");
        assertThat(participant.getJoinedAt()).isNotNull();

        // Attempting to mutate would be caught at compile time —
        // there are no setters. Verify fields are final.
        assertThat(Participant.class.getDeclaredFields())
                .allMatch(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }
}
