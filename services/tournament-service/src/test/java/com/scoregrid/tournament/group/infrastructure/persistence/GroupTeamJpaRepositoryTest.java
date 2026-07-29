package com.scoregrid.tournament.group.infrastructure.persistence;

import com.scoregrid.tournament.TestcontainersConfiguration;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaEntity;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaRepository;
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
class GroupTeamJpaRepositoryTest {

    @Autowired
    private GroupTeamJpaRepository jpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private TournamentJpaRepository tournamentJpaRepository;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TestEntityManager em;

    private Long tournamentId;
    private Long group1Id;
    private Long group2Id;
    private Long team7Id;
    private Long team8Id;

    @BeforeEach
    void setUp() {
        var tournament = new TournamentJpaEntity();
        tournament.setName("Copa Test");
        tournament.setStatus("ACTIVE");
        tournament.setCreatedBy("42");
        tournamentId = tournamentJpaRepository.save(tournament).getId();

        var team7 = new TeamJpaEntity();
        team7.setName("Argentina");
        team7.setShortName("ARG");
        team7Id = teamJpaRepository.save(team7).getId();

        var team8 = new TeamJpaEntity();
        team8.setName("Brazil");
        team8.setShortName("BRA");
        team8Id = teamJpaRepository.save(team8).getId();

        var g1 = new GroupJpaEntity();
        g1.setTournamentId(tournamentId);
        g1.setName("Grupo A");
        g1.setDisplayOrder(0);
        group1Id = groupJpaRepository.save(g1).getId();

        var g2 = new GroupJpaEntity();
        g2.setTournamentId(tournamentId);
        g2.setName("Grupo B");
        g2.setDisplayOrder(1);
        group2Id = groupJpaRepository.save(g2).getId();

        em.flush();
        em.clear();
    }

    @Test
    void shouldAssignAndFindByGroup() {
        jpaRepository.save(new GroupTeamJpaEntity(group1Id, team7Id));
        jpaRepository.save(new GroupTeamJpaEntity(group1Id, team8Id));
        em.flush();
        em.clear();

        var entries = jpaRepository.findByGroupId(group1Id);
        assertThat(entries).hasSize(2);
    }

    @Test
    void shouldCheckExistenceByGroupAndTeam() {
        jpaRepository.save(new GroupTeamJpaEntity(group1Id, team7Id));
        em.flush();
        em.clear();

        assertThat(jpaRepository.existsByGroupIdAndTeamId(group1Id, team7Id)).isTrue();
        assertThat(jpaRepository.existsByGroupIdAndTeamId(group1Id, team8Id)).isFalse();
    }

    @Test
    void shouldFindGroupIdByTeamIdAndTournamentId() {
        jpaRepository.save(new GroupTeamJpaEntity(group1Id, team7Id));
        em.flush();
        em.clear();

        var found = jpaRepository.findGroupIdByTeamIdAndTournamentId(team7Id, tournamentId);
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(group1Id);
    }

    @Test
    void shouldReturnEmptyWhenTeamNotInAnyGroup() {
        var found = jpaRepository.findGroupIdByTeamIdAndTournamentId(99L, tournamentId);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnCorrectGroupWhenTeamInAnotherGroupSameTournament() {
        jpaRepository.save(new GroupTeamJpaEntity(group1Id, team7Id));
        em.flush();
        em.clear();

        var found = jpaRepository.findGroupIdByTeamIdAndTournamentId(team7Id, tournamentId);
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(group1Id);
    }
}
