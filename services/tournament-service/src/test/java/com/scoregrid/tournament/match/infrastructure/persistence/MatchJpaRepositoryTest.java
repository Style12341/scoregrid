package com.scoregrid.tournament.match.infrastructure.persistence;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MatchJpaRepositoryTest {

    @Autowired
    private MatchJpaRepository jpaRepository;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TournamentJpaRepository tournamentJpaRepository;

    @Autowired
    private TestEntityManager em;

    private Long tournamentId;
    private TeamJpaEntity homeTeam;
    private TeamJpaEntity awayTeam;

    @BeforeEach
    void setUp() {
        var tournament = new TournamentJpaEntity();
        tournament.setName("Copa Test");
        tournament.setStatus("ACTIVE");
        tournament.setCreatedBy("42");
        tournamentId = tournamentJpaRepository.save(tournament).getId();

        homeTeam = new TeamJpaEntity();
        homeTeam.setName("Argentina");
        homeTeam.setShortName("ARG");
        homeTeam = teamJpaRepository.save(homeTeam);

        awayTeam = new TeamJpaEntity();
        awayTeam.setName("Brazil");
        awayTeam.setShortName("BRA");
        awayTeam = teamJpaRepository.save(awayTeam);

        em.flush();
        em.clear();
    }

    @Test
    void shouldPersistAndFindMatch() {
        var entity = new MatchEntity();
        entity.setTournamentId(tournamentId);
        entity.setHomeTeam(homeTeam);
        entity.setAwayTeam(awayTeam);
        entity.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        entity.setStatus("SCHEDULED");

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("SCHEDULED");
        assertThat(found.get().getHomeTeam().getName()).isEqualTo("Argentina");
        assertThat(found.get().getAwayTeam().getShortName()).isEqualTo("BRA");
        assertThat(found.get().getStartTime()).isEqualTo(Instant.parse("2026-08-14T18:30:00Z"));
    }

    @Test
    void shouldFindByTournamentId() {
        var m1 = new MatchEntity();
        m1.setTournamentId(tournamentId);
        m1.setHomeTeam(homeTeam);
        m1.setAwayTeam(awayTeam);
        m1.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        m1.setStatus("SCHEDULED");
        jpaRepository.save(m1);

        var m2 = new MatchEntity();
        m2.setTournamentId(tournamentId);
        m2.setHomeTeam(homeTeam);
        m2.setAwayTeam(awayTeam);
        m2.setStartTime(Instant.parse("2026-08-15T20:00:00Z"));
        m2.setStatus("SCHEDULED");
        jpaRepository.save(m2);

        em.flush();
        em.clear();

        var matches = jpaRepository.findByTournamentIdOrderByStartTimeAsc(tournamentId);
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getStartTime()).isBefore(matches.get(1).getStartTime());
    }

    @Test
    void shouldFindByTournamentIdAndStatus() {
        var m = new MatchEntity();
        m.setTournamentId(tournamentId);
        m.setHomeTeam(homeTeam);
        m.setAwayTeam(awayTeam);
        m.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        m.setStatus("SCHEDULED");
        jpaRepository.save(m);

        em.flush();
        em.clear();

        var scheduled = jpaRepository.findByTournamentIdAndStatusOrderByStartTimeAsc(
                tournamentId, "SCHEDULED");
        assertThat(scheduled).hasSize(1);

        var finished = jpaRepository.findByTournamentIdAndStatusOrderByStartTimeAsc(
                tournamentId, "FINISHED");
        assertThat(finished).isEmpty();
    }

    @Test
    void shouldStoreScoresAsNull() {
        var entity = new MatchEntity();
        entity.setTournamentId(tournamentId);
        entity.setHomeTeam(homeTeam);
        entity.setAwayTeam(awayTeam);
        entity.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        entity.setStatus("SCHEDULED");

        var saved = jpaRepository.save(entity);
        em.flush();
        em.clear();

        var found = jpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getHomeScore()).isNull();
        assertThat(found.getAwayScore()).isNull();
    }
}
