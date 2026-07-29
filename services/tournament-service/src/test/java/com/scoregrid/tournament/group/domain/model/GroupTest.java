package com.scoregrid.tournament.group.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupTest {

    @Test
    void shouldCreateGroup() {
        var group = Group.create(1L, "Grupo A", 1);

        assertThat(group.getTournamentId()).isEqualTo(1L);
        assertThat(group.getName()).isEqualTo("Grupo A");
        assertThat(group.getDisplayOrder()).isEqualTo(1);
        assertThat(group.getId()).isNull();
    }

    @Test
    void shouldCreateGroupWithDefaultOrder() {
        var group = Group.create(1L, "Grupo B", 0);

        assertThat(group.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void shouldReconstituteGroup() {
        var group = Group.reconstitute(3L, 1L, "Grupo A", 1);

        assertThat(group.getId()).isEqualTo(3L);
        assertThat(group.getTournamentId()).isEqualTo(1L);
        assertThat(group.getName()).isEqualTo("Grupo A");
        assertThat(group.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void shouldSetId() {
        var group = Group.create(1L, "Grupo A", 0);
        group.setId(5L);
        assertThat(group.getId()).isEqualTo(5L);
    }
}
