package com.scoregrid.tournament.phase.infrastructure.persistence;

import com.scoregrid.tournament.TestcontainersConfiguration;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaEntity;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PhaseJpaRepositoryTest {

    @Autowired
    private PhaseJpaRepository jpaRepository;

    @Autowired
    private TournamentJpaRepository tournamentJpaRepository;

    @Autowired
    private TestEntityManager em;

    private Long tournamentId;

    @BeforeEach
    void setUp() {
        var tournament = new TournamentJpaEntity();
        tournament.setName("Copa Test");
        tournament.setStatus("ACTIVE");
        tournament.setCreatedBy("42");
        tournamentId = tournamentJpaRepository.save(tournament).getId();
        em.flush();
        em.clear();
    }

    @Test
    void shouldPersistAndFindPhase() {
        var entity = new PhaseJpaEntity();
        entity.setTournamentId(tournamentId);
        entity.setName("Semifinal");
        entity.setType("SEMI_FINAL");
        entity.setDisplayOrder(4);

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Semifinal");
        assertThat(found.get().getType()).isEqualTo("SEMI_FINAL");
        assertThat(found.get().getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void shouldPersistPhaseWithNullName() {
        var entity = new PhaseJpaEntity();
        entity.setTournamentId(tournamentId);
        entity.setType("FINAL");
        entity.setDisplayOrder(5);

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isNull();
    }

    @Test
    void shouldFindByTournamentIdOrderedByDisplayOrder() {
        var p1 = new PhaseJpaEntity();
        p1.setTournamentId(tournamentId);
        p1.setType("GROUP_STAGE");
        p1.setDisplayOrder(0);
        jpaRepository.save(p1);

        var p2 = new PhaseJpaEntity();
        p2.setTournamentId(tournamentId);
        p2.setType("FINAL");
        p2.setDisplayOrder(5);
        jpaRepository.save(p2);

        em.flush();
        em.clear();

        var phases = jpaRepository.findByTournamentIdOrderByDisplayOrderAsc(tournamentId);
        assertThat(phases).hasSize(2);
        assertThat(phases.get(0).getType()).isEqualTo("GROUP_STAGE");
        assertThat(phases.get(1).getType()).isEqualTo("FINAL");
    }
}
