package com.scoregrid.tournament.match;

import com.scoregrid.tournament.TestcontainersConfiguration;
import com.scoregrid.tournament.match.infrastructure.persistence.MatchEntity;
import com.scoregrid.tournament.match.infrastructure.persistence.MatchJpaRepository;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaEntity;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaRepository;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaEntity;
import com.scoregrid.tournament.tournament.infrastructure.persistence.TournamentJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional
class TournamentServiceMatchFlowIntegrationTest {

    @Autowired
    private MatchJpaRepository matchJpaRepository;

    @Autowired
    private TournamentJpaRepository tournamentJpaRepository;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private EntityManager em;

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
    void shouldFlowCreateUpdateResultMatch() {
        // 1. Create a SCHEDULED match
        var entity = new MatchEntity();
        entity.setTournamentId(tournamentId);
        entity.setHomeTeam(homeTeam);
        entity.setAwayTeam(awayTeam);
        entity.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        entity.setStatus("SCHEDULED");
        var saved = matchJpaRepository.save(entity);
        em.flush();
        em.clear();

        // 2. Read back and verify
        var found = matchJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo("SCHEDULED");
        assertThat(found.getHomeTeam().getName()).isEqualTo("Argentina");
        assertThat(found.getHomeScore()).isNull();
        assertThat(found.getAwayScore()).isNull();

        // 3. Update startTime
        found.setStartTime(Instant.parse("2026-08-15T20:00:00Z"));
        matchJpaRepository.save(found);
        em.flush();
        em.clear();

        var updated = matchJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStartTime()).isEqualTo("2026-08-15T20:00:00Z");

        // 4. Load result
        var toFinish = matchJpaRepository.findById(saved.getId()).orElseThrow();
        toFinish.setHomeScore(2);
        toFinish.setAwayScore(1);
        toFinish.setStatus("FINISHED");
        matchJpaRepository.save(toFinish);
        em.flush();
        em.clear();

        var finished = matchJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo("FINISHED");
        assertThat(finished.getHomeScore()).isEqualTo(2);
        assertThat(finished.getAwayScore()).isEqualTo(1);
    }

    @Test
    void shouldFilterByStatus() {
        var m1 = new MatchEntity();
        m1.setTournamentId(tournamentId);
        m1.setHomeTeam(homeTeam);
        m1.setAwayTeam(awayTeam);
        m1.setStartTime(Instant.parse("2026-08-14T18:30:00Z"));
        m1.setStatus("SCHEDULED");
        matchJpaRepository.save(m1);

        var m2 = new MatchEntity();
        m2.setTournamentId(tournamentId);
        m2.setHomeTeam(homeTeam);
        m2.setAwayTeam(awayTeam);
        m2.setStartTime(Instant.parse("2026-08-15T20:00:00Z"));
        m2.setStatus("FINISHED");
        matchJpaRepository.save(m2);

        em.flush();
        em.clear();

        var scheduled = matchJpaRepository.findByTournamentIdAndStatusOrderByStartTimeAsc(
                tournamentId, "SCHEDULED");
        assertThat(scheduled).hasSize(1);

        var finished = matchJpaRepository.findByTournamentIdAndStatusOrderByStartTimeAsc(
                tournamentId, "FINISHED");
        assertThat(finished).hasSize(1);
    }
}
