package com.scoregrid.tournament.phase.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseTest {

    @Test
    void shouldCreatePhaseWithAllFields() {
        var phase = Phase.create(1L, PhaseType.SEMI_FINAL, "Semifinal", 4);

        assertThat(phase.getTournamentId()).isEqualTo(1L);
        assertThat(phase.getType()).isEqualTo(PhaseType.SEMI_FINAL);
        assertThat(phase.getName()).isEqualTo("Semifinal");
        assertThat(phase.getDisplayOrder()).isEqualTo(4);
        assertThat(phase.getId()).isNull();
    }

    @Test
    void shouldCreatePhaseWithNullName() {
        var phase = Phase.create(1L, PhaseType.FINAL, null, 5);

        assertThat(phase.getName()).isNull();
        assertThat(phase.getType()).isEqualTo(PhaseType.FINAL);
    }

    @Test
    void shouldCreatePhaseWithDefaultOrder() {
        var phase = Phase.create(1L, PhaseType.GROUP_STAGE, null, 0);

        assertThat(phase.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void shouldReconstitutePhase() {
        var phase = Phase.reconstitute(5L, 1L, "Semifinal", PhaseType.SEMI_FINAL, 4);

        assertThat(phase.getId()).isEqualTo(5L);
        assertThat(phase.getTournamentId()).isEqualTo(1L);
        assertThat(phase.getName()).isEqualTo("Semifinal");
        assertThat(phase.getType()).isEqualTo(PhaseType.SEMI_FINAL);
        assertThat(phase.getDisplayOrder()).isEqualTo(4);
    }
}
