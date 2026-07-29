package com.scoregrid.tournament.phase.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseTypeTest {

    @Test
    void shouldContainAllPhaseTypes() {
        PhaseType[] values = PhaseType.values();
        assertThat(values).containsExactly(
                PhaseType.GROUP_STAGE,
                PhaseType.ROUND_OF_16,
                PhaseType.QUARTER_FINAL,
                PhaseType.SEMI_FINAL,
                PhaseType.THIRD_PLACE,
                PhaseType.FINAL
        );
    }

    @Test
    void shouldParseByEnumName() {
        assertThat(PhaseType.valueOf("SEMI_FINAL")).isEqualTo(PhaseType.SEMI_FINAL);
        assertThat(PhaseType.valueOf("FINAL")).isEqualTo(PhaseType.FINAL);
    }
}
