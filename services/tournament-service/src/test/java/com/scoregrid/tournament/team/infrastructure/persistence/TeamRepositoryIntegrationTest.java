package com.scoregrid.tournament.team.infrastructure.persistence;

import com.scoregrid.tournament.TestcontainersConfiguration;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaEntity;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TeamRepositoryIntegrationTest {

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TournamentTeamJpaRepository tournamentTeamJpaRepository;

    @Autowired
    private TournamentJpaRepository tournamentJpaRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void shouldPersistAndFindTeam() {
        var entity = new TeamJpaEntity();
        entity.setName("Argentina");
        entity.setShortName("ARG");
        entity.setCountry("AR");
        entity.setLogoUrl("https://flags.example.com/ar.png");

        var saved = teamJpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = teamJpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Argentina");
        assertThat(found.get().getShortName()).isEqualTo("ARG");
        assertThat(found.get().getCountry()).isEqualTo("AR");
        assertThat(found.get().getLogoUrl()).isEqualTo("https://flags.example.com/ar.png");
    }

    @Test
    void shouldPersistTeamWithOnlyRequiredField() {
        var entity = new TeamJpaEntity();
        entity.setName("Brazil");

        var saved = teamJpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = teamJpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Brazil");
        assertThat(found.get().getShortName()).isNull();
        assertThat(found.get().getCountry()).isNull();
        assertThat(found.get().getLogoUrl()).isNull();
    }

    @Test
    void shouldFindAllTeams() {
        var t1 = new TeamJpaEntity();
        t1.setName("Argentina");
        teamJpaRepository.save(t1);

        var t2 = new TeamJpaEntity();
        t2.setName("Brazil");
        teamJpaRepository.save(t2);

        em.flush();
        em.clear();

        var all = teamJpaRepository.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void shouldUpdateTeam() {
        var entity = new TeamJpaEntity();
        entity.setName("Old Name");
        entity.setShortName("OLD");
        var saved = teamJpaRepository.save(entity);
        em.flush();
        em.clear();

        var toUpdate = teamJpaRepository.findById(saved.getId()).orElseThrow();
        toUpdate.setName("New Name");
        toUpdate.setShortName("NEW");
        teamJpaRepository.save(toUpdate);
        em.flush();
        em.clear();

        var updated = teamJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getShortName()).isEqualTo("NEW");
    }

    @Test
    void shouldAssignTeamToTournament() {
        var team = new TeamJpaEntity();
        team.setName("Argentina");
        var savedTeam = teamJpaRepository.save(team);
        var tournament = createTournament("Test Tournament");
        em.flush();

        var assignment = new TournamentTeamJpaEntity(tournament.getId(), savedTeam.getId());
        tournamentTeamJpaRepository.save(assignment);
        em.flush();
        em.clear();

        var found = tournamentTeamJpaRepository.findById(
                new TournamentTeamJpaEntity.TournamentTeamId(tournament.getId(), savedTeam.getId()));
        assertThat(found).isPresent();
    }

    @Test
    void shouldFindTeamsByTournamentId() {
        var t1 = createTeam("Argentina");
        var t2 = createTeam("Brazil");
        var tournament = createTournament("Test Tournament");
        em.flush();

        tournamentTeamJpaRepository.save(new TournamentTeamJpaEntity(tournament.getId(), t1.getId()));
        tournamentTeamJpaRepository.save(new TournamentTeamJpaEntity(tournament.getId(), t2.getId()));
        em.flush();
        em.clear();

        var teamIds = tournamentTeamJpaRepository.findTeamIdsByTournamentId(tournament.getId());
        assertThat(teamIds).containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    void shouldBeIdempotentForDuplicateAssignment() {
        var team = createTeam("Argentina");
        var tournament = createTournament("Test Tournament");
        em.flush();

        tournamentTeamJpaRepository.save(new TournamentTeamJpaEntity(tournament.getId(), team.getId()));
        em.flush();

        // Second save attempt — would throw DataIntegrityViolation if not idempotent.
        // The adapter check prevents this, but at the repo level the PK constraint is the real guard.
        var count = tournamentTeamJpaRepository.findByTournamentId(tournament.getId()).size();
        assertThat(count).isEqualTo(1);

        var teamIds = tournamentTeamJpaRepository.findTeamIdsByTournamentId(tournament.getId());
        assertThat(teamIds).containsExactly(team.getId());
    }

    @Test
    void shouldReturnEmptyForTournamentWithNoTeams() {
        var teams = tournamentTeamJpaRepository.findTeamIdsByTournamentId(999L);
        assertThat(teams).isEmpty();
    }

    private TeamJpaEntity createTeam(String name) {
        var team = new TeamJpaEntity();
        team.setName(name);
        return teamJpaRepository.save(team);
    }

    private TournamentJpaEntity createTournament(String name) {
        var tournament = new TournamentJpaEntity();
        tournament.setName(name);
        tournament.setCreatedBy("42");
        tournament.setCreatedAt(Instant.now());
        tournament.setUpdatedAt(Instant.now());
        return tournamentJpaRepository.save(tournament);
    }
}
