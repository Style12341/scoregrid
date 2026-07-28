package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.model.ScoredPrediction;
import com.scoregrid.score.score.domain.model.TournamentRankingEntry;
import com.scoregrid.score.score.domain.port.out.AuthClientPort;
import com.scoregrid.score.score.domain.port.out.MatchScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRankingsServiceTest {

    @Mock
    private MatchScoreRepository matchScoreRepository;

    @Mock
    private AuthClientPort authClient;

    @Test
    @DisplayName("C2: tie-break sorts exactHits desc, not asc")
    void tieBreakSortsExactHitsDescending() {
        var spAna = new ScoredPrediction("1", "p1", 2, 1, 3, true, true);
        var spCaro = new ScoredPrediction("2", "p2", 2, 1, 3, true, true);
        var spBeto = new ScoredPrediction("3", "p3", 2, 1, 3, true, true);

        var spCaro2 = new ScoredPrediction("2", "p5", 1, 0, 3, true, true);
        var spBeto2 = new ScoredPrediction("3", "p6", 1, 0, 3, true, true);
        var spBeto3 = new ScoredPrediction("3", "p7", 0, 0, 3, true, true);
        var spBeto4 = new ScoredPrediction("3", "p8", 3, 1, 3, true, true);

        // ana = 1 exactHit, caro = 2 exactHits, beto = 4 exactHits — all 10 points
        var ms1 = matchScore("t1", spAna, spCaro, spBeto, 
                new ScoredPrediction("2", "p9", 0, 1, 1, true, false),
                new ScoredPrediction("3", "p10", 2, 2, 1, true, false));

        var ms2 = matchScore("t1", new ScoredPrediction("2", "p11", 1, 0, 3, true, true),
                new ScoredPrediction("3", "p12", 1, 0, 3, true, true));

        var ms3 = matchScore("t1", new ScoredPrediction("3", "p13", 0, 0, 3, true, true));

        when(matchScoreRepository.findAllByTournamentId("t1")).thenReturn(List.of(ms1, ms2, ms3));
        when(authClient.getUsernames(anyList())).thenReturn(Map.of("1", "ana", "2", "caro", "3", "beto"));

        var service = new GetRankingsService(matchScoreRepository, authClient);
        var ranking = service.getTournamentRanking("t1", 0, 50);

        assertThat(ranking).hasSize(3);

        // All have 10 points, so exactHits tie-break decides
        // ana should be last (1 exactHit), caro middle (2), beto first (4)
        assertThat(ranking.get(0).userId()).isEqualTo("3"); // beto: 4 exactHits
        assertThat(ranking.get(0).position()).isEqualTo(1);
        assertThat(ranking.get(1).userId()).isEqualTo("2"); // caro: 2 exactHits
        assertThat(ranking.get(1).position()).isEqualTo(2);
        assertThat(ranking.get(2).userId()).isEqualTo("1"); // ana: 1 exactHit
        assertThat(ranking.get(2).position()).isEqualTo(3);

        // Verify the exactHits are in descending order
        assertThat(ranking.get(0).exactHits()).isGreaterThanOrEqualTo(ranking.get(1).exactHits());
        assertThat(ranking.get(1).exactHits()).isGreaterThanOrEqualTo(ranking.get(2).exactHits());
    }

    @Test
    @DisplayName("C3: tournamentsPlayed credits every user, not just first in list")
    void tournamentsPlayedCreditsAllUsers() {
        // ana appears first in list, lucia second
        var spAna = new ScoredPrediction("1", "p1", 2, 1, 3, true, true);
        var spLucia = new ScoredPrediction("2", "p2", 1, 0, 0, false, false);

        var ms1 = matchScore("t1", spAna, spLucia);
        var ms2 = matchScore("t1", spAna, spLucia);

        when(matchScoreRepository.findAll()).thenReturn(List.of(ms1, ms2));
        when(authClient.getUsernames(anyList())).thenReturn(Map.of("1", "ana", "2", "lucia"));

        var service = new GetRankingsService(matchScoreRepository, authClient);
        var ranking = service.getGlobalRanking(0, 50);

        assertThat(ranking).hasSize(2);

        var ana = ranking.stream().filter(e -> e.userId().equals("1")).findFirst().orElseThrow();
        var lucia = ranking.stream().filter(e -> e.userId().equals("2")).findFirst().orElseThrow();

        assertThat(ana.tournamentsPlayed()).isEqualTo(1);
        assertThat(lucia.tournamentsPlayed()).isEqualTo(1);
    }

    private MatchScore matchScore(String tournamentId, ScoredPrediction... scores) {
        return new MatchScore("m" + System.nanoTime(), tournamentId, 2, 1, "HOME_WIN",
                scores.length, 0, Instant.now(), List.of(scores));
    }
}
