package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.TestcontainersConfiguration;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaEntity;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaRepository;
import com.scoregrid.tournament.team.infrastructure.persistence.TournamentTeamJpaEntity;
import com.scoregrid.tournament.team.infrastructure.persistence.TournamentTeamJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TournamentRepositoryIntegrationTest {

    @Autowired
    private TournamentJpaRepository jpaRepository;

    @Autowired
    private ParticipantJpaRepository participantJpaRepository;

    @Autowired
    private TournamentTeamJpaRepository tournamentTeamJpaRepository;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void shouldPersistAndFindTournament() {
        var entity = new TournamentJpaEntity();
        entity.setName("Copa 2026");
        entity.setStatus("DRAFT");
        entity.setCreatedBy("42");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Copa 2026");
        assertThat(found.get().getStatus()).isEqualTo("DRAFT");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void shouldDefaultStatusToDraft() {
        var entity = new TournamentJpaEntity();
        entity.setName("Copa Draft");
        entity.setCreatedBy("42");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void shouldFindByStatus() {
        var active = new TournamentJpaEntity();
        active.setName("Active");
        active.setStatus("ACTIVE");
        active.setCreatedBy("42");
        active.setCreatedAt(Instant.now());
        active.setUpdatedAt(Instant.now());
        jpaRepository.save(active);

        var draft = new TournamentJpaEntity();
        draft.setName("Draft");
        draft.setStatus("DRAFT");
        draft.setCreatedBy("42");
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        jpaRepository.save(draft);

        em.flush();
        em.clear();

        var activesPage = jpaRepository.findAllByStatus("ACTIVE", PageRequest.of(0, 20));
        assertThat(activesPage.getContent()).hasSize(1);
        assertThat(activesPage.getContent().get(0).getName()).isEqualTo("Active");
    }

    @Test
    void shouldDeleteTournamentWithCascade() {
        // 1. Create a team
        var team = new TeamJpaEntity();
        team.setName("Argentina");
        team.setShortName("ARG");
        var savedTeam = teamJpaRepository.save(team);

        // 2. Create a tournament
        var tournament = new TournamentJpaEntity();
        tournament.setName("CascadeDelete");
        tournament.setStatus("DRAFT");
        tournament.setCreatedBy("42");
        tournament.setCreatedAt(Instant.now());
        tournament.setUpdatedAt(Instant.now());
        var savedTournament = jpaRepository.save(tournament);
        em.flush();

        // 3. Assign the team to the tournament
        tournamentTeamJpaRepository.save(
                new TournamentTeamJpaEntity(savedTournament.getId(), savedTeam.getId()));

        // 4. Enrol a participant
        var participant = new ParticipantJpaEntity();
        participant.setTournamentId(savedTournament.getId());
        participant.setUserId("42");
        participant.setJoinedAt(Instant.now());
        participantJpaRepository.save(participant);
        em.flush();
        em.clear();

        // 5. Verify rows exist before delete
        assertThat(tournamentTeamJpaRepository.findByTournamentId(savedTournament.getId())).isNotEmpty();
        assertThat(participantJpaRepository.findByTournamentId(savedTournament.getId())).isNotEmpty();

        // 6. Delete the tournament — cascade should remove related rows
        jpaRepository.deleteById(savedTournament.getId());
        em.flush();
        em.clear();

        // 7. Assert tournament is gone
        assertThat(jpaRepository.findById(savedTournament.getId())).isEmpty();

        // 8. Assert tournament_teams are gone (cascade V3)
        assertThat(tournamentTeamJpaRepository.findByTournamentId(savedTournament.getId())).isEmpty();

        // 9. Assert tournament_participants are gone (cascade V4)
        assertThat(participantJpaRepository.findByTournamentId(savedTournament.getId())).isEmpty();
    }

    @Test
    void shouldCountByStatus() {
        var entity = new TournamentJpaEntity();
        entity.setName("Counted");
        entity.setStatus("ACTIVE");
        entity.setCreatedBy("42");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepository.save(entity);
        em.flush();

        assertThat(jpaRepository.countByStatus("ACTIVE")).isEqualTo(1);
        assertThat(jpaRepository.countByStatus("DRAFT")).isEqualTo(0);
    }

    @Test
    void shouldPaginateByStatus() {
        // Create 5 ACTIVE tournaments
        for (int i = 0; i < 5; i++) {
            var entity = new TournamentJpaEntity();
            entity.setName("Active " + i);
            entity.setStatus("ACTIVE");
            entity.setCreatedBy("42");
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            jpaRepository.save(entity);
        }
        em.flush();
        em.clear();

        // Page 0 (size 2) — should return exactly 2 results
        var page0 = jpaRepository.findAllByStatus("ACTIVE", PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);

        // Page 1 (size 2) — should return exactly 2 results
        var page1 = jpaRepository.findAllByStatus("ACTIVE", PageRequest.of(1, 2));
        assertThat(page1.getContent()).hasSize(2);

        // Page 2 (size 2) — should return exactly 1 result
        var page2 = jpaRepository.findAllByStatus("ACTIVE", PageRequest.of(2, 2));
        assertThat(page2.getContent()).hasSize(1);
    }
}
