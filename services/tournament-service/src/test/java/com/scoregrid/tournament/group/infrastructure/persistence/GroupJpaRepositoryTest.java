package com.scoregrid.tournament.group.infrastructure.persistence;

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
class GroupJpaRepositoryTest {

    @Autowired
    private GroupJpaRepository jpaRepository;

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
    void shouldPersistAndFindGroup() {
        var entity = new GroupJpaEntity();
        entity.setTournamentId(tournamentId);
        entity.setName("Grupo A");
        entity.setDisplayOrder(1);

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Grupo A");
        assertThat(found.get().getDisplayOrder()).isEqualTo(1);
        assertThat(found.get().getTournamentId()).isEqualTo(tournamentId);
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void shouldFindByTournamentIdOrderedByDisplayOrder() {
        var g1 = new GroupJpaEntity();
        g1.setTournamentId(tournamentId);
        g1.setName("Grupo B");
        g1.setDisplayOrder(1);
        jpaRepository.save(g1);

        var g2 = new GroupJpaEntity();
        g2.setTournamentId(tournamentId);
        g2.setName("Grupo A");
        g2.setDisplayOrder(0);
        jpaRepository.save(g2);

        em.flush();
        em.clear();

        var groups = jpaRepository.findByTournamentIdOrderByDisplayOrderAsc(tournamentId);
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getName()).isEqualTo("Grupo A");
        assertThat(groups.get(1).getName()).isEqualTo("Grupo B");
    }

    @Test
    void shouldReturnEmptyListForTournamentWithNoGroups() {
        var groups = jpaRepository.findByTournamentIdOrderByDisplayOrderAsc(999L);
        assertThat(groups).isEmpty();
    }
}
