package com.scoregrid.tournament.match.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRefTest {

    @Test
    void shouldCreateTeamRef() {
        var ref = TeamRef.of(7L, "Argentina", "ARG");

        assertThat(ref.id()).isEqualTo(7L);
        assertThat(ref.name()).isEqualTo("Argentina");
        assertThat(ref.shortName()).isEqualTo("ARG");
    }

    @Test
    void equalsShouldBeBasedOnIdOnly() {
        var ref1 = TeamRef.of(7L, "Argentina", "ARG");
        var ref2 = TeamRef.of(7L, "Argentina (alt)", "ARG");

        assertThat(ref1).isEqualTo(ref2);
        assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
    }

    @Test
    void shouldNotEqualDifferentId() {
        var ref1 = TeamRef.of(7L, "Argentina", "ARG");
        var ref2 = TeamRef.of(8L, "Argentina", "ARG");

        assertThat(ref1).isNotEqualTo(ref2);
    }
}
