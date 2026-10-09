package com.scoregrid.score.score.infrastructure.persistence;

import com.scoregrid.score.TestcontainersConfiguration;
import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.model.ScoredPrediction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import({TestcontainersConfiguration.class, MatchScoreRepositoryAdapter.class})
class MatchScoreRepositoryAdapterTest {

    @Autowired
    private MatchScoreRepositoryAdapter adapter;

    @Autowired
    private MongoMatchScoreRepository mongoRepo;

    @BeforeEach
    void cleanCollection() {
        mongoRepo.deleteAll();
    }

    @Test
    void userQueryExcludesOtherMatchesAndReadsCorrectedPoints() {
        adapter.save(matchScore("m1",
                new ScoredPrediction("42", "p1", 2, 1, 3, true, true),
                new ScoredPrediction("7", "p2", 1, 0, 0, false, false)));
        adapter.save(matchScore("m2",
                new ScoredPrediction("7", "p3", 1, 0, 1, true, false)));

        assertThat(adapter.findAllByUserId("42"))
                .extracting(MatchScore::matchId)
                .containsExactly("m1");

        adapter.save(matchScore("m1",
                new ScoredPrediction("42", "p1", 2, 1, 0, false, false),
                new ScoredPrediction("7", "p2", 1, 0, 1, true, false)));

        var corrected = adapter.findAllByUserId("42");
        assertThat(corrected).hasSize(1);
        assertThat(corrected.getFirst().individualScores().getFirst().points()).isZero();
        assertThat(mongoRepo.count()).isEqualTo(2);
    }

    private static MatchScore matchScore(String matchId, ScoredPrediction... scores) {
        return new MatchScore(matchId, "t1", 2, 1, "HOME_WIN", scores.length, 0,
                Instant.now(), List.of(scores));
    }
}
