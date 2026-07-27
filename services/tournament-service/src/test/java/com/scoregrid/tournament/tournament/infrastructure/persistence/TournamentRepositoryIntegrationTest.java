package com.scoregrid.tournament.tournament.infrastructure.persistence;

import com.scoregrid.tournament.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TournamentRepositoryIntegrationTest {

    @Autowired
    private TournamentJpaRepository jpaRepository;

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

        var actives = jpaRepository.findAllByStatus("ACTIVE");
        assertThat(actives).hasSize(1);
        assertThat(actives.get(0).getName()).isEqualTo("Active");
    }

    @Test
    void shouldDeleteTournament() {
        var entity = new TournamentJpaEntity();
        entity.setName("ToDelete");
        entity.setStatus("DRAFT");
        entity.setCreatedBy("42");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        var saved = jpaRepository.save(entity);
        em.flush();

        jpaRepository.deleteById(saved.getId());
        em.flush();

        assertThat(jpaRepository.findById(saved.getId())).isEmpty();
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
}
