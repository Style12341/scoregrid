package com.scoregrid.tournament.team.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateTeamWithAllFields() {
            var team = Team.create("Argentina", "ARG", "AR", "https://flags.example.com/ar.png");

            assertThat(team.getName()).isEqualTo("Argentina");
            assertThat(team.getShortName()).isEqualTo("ARG");
            assertThat(team.getCountry()).isEqualTo("AR");
            assertThat(team.getLogoUrl()).isEqualTo("https://flags.example.com/ar.png");
        }

        @Test
        void shouldCreateTeamWithOnlyRequiredField() {
            var team = Team.create("Argentina", null, null, null);

            assertThat(team.getName()).isEqualTo("Argentina");
            assertThat(team.getShortName()).isNull();
            assertThat(team.getCountry()).isNull();
            assertThat(team.getLogoUrl()).isNull();
        }

        @Test
        void shouldCreateTeamWithoutLogo() {
            var team = Team.create("Brazil", "BRA", "BR", null);

            assertThat(team.getName()).isEqualTo("Brazil");
            assertThat(team.getShortName()).isEqualTo("BRA");
            assertThat(team.getCountry()).isEqualTo("BR");
            assertThat(team.getLogoUrl()).isNull();
        }
    }

    @Nested
    class Identity {

        @Test
        void shouldNotHaveIdBeforePersistence() {
            var team = Team.create("Uruguay", "URU", "UY", null);

            assertThat(team.getId()).isNull();
        }

        @Test
        void shouldAcceptIdAfterPersistence() {
            var team = Team.create("Uruguay", "URU", "UY", null);
            team.setId(7L);

            assertThat(team.getId()).isEqualTo(7L);
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateAllFields() {
            var team = Team.create("Old Name", "OLD", "XX", "https://old.example.com");
            team.setId(1L);

            team.update("Argentina", "ARG", "AR", "https://flags.example.com/ar.png");

            assertThat(team.getName()).isEqualTo("Argentina");
            assertThat(team.getShortName()).isEqualTo("ARG");
            assertThat(team.getCountry()).isEqualTo("AR");
            assertThat(team.getLogoUrl()).isEqualTo("https://flags.example.com/ar.png");
            assertThat(team.getId()).isEqualTo(1L); // id unchanged
        }

        @Test
        void shouldUpdateNameOnly() {
            var team = Team.create("Old Name", "OLD", "XX", "https://old.example.com");

            team.update("New Name", null, null, null);

            assertThat(team.getName()).isEqualTo("New Name");
            assertThat(team.getShortName()).isNull();
            assertThat(team.getCountry()).isNull();
            assertThat(team.getLogoUrl()).isNull();
        }

        @Test
        void shouldClearOptionalFields() {
            var team = Team.create("Argentina", "ARG", "AR", "https://flags.example.com/ar.png");

            team.update("Argentina", "", "", null);

            assertThat(team.getName()).isEqualTo("Argentina");
            assertThat(team.getShortName()).isEmpty();
            assertThat(team.getCountry()).isEmpty();
            assertThat(team.getLogoUrl()).isNull();
        }
    }
}
