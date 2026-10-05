package com.scoregrid.prediction.prediction.infrastructure.persistence;

import com.scoregrid.prediction.TestcontainersConfiguration;
import com.scoregrid.prediction.prediction.domain.model.DerivedOutcome;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.model.PredictionType;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import({TestcontainersConfiguration.class, PredictionRepositoryAdapter.class})
class PredictionRepositoryAdapterTest {

    @Autowired
    private PredictionRepositoryAdapter adapter;

    @Autowired
    private MongoPredictionRepository mongoRepo;

    @BeforeEach
    void cleanCollection() {
        mongoRepo.deleteAll();
    }

    @Test
    @DisplayName("the loser of two concurrent duplicate inserts gets 409 DUPLICATE_PREDICTION, not a 500")
    void concurrentDuplicate_loserGetsDuplicatePrediction() throws Exception {
        // Both requests already passed CreatePredictionService's existsBy
        // check; only the unique index can decide between them.
        int racers = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Prediction>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(racers)) {
            for (int i = 0; i < racers; i++) {
                int homeScore = i;
                Callable<Prediction> insert = () -> {
                    start.await();
                    return adapter.save(newPrediction("user-1", "match-1", homeScore));
                };
                results.add(pool.submit(insert));
            }
            start.countDown();

            int saved = 0;
            List<DomainException> rejected = new ArrayList<>();
            for (Future<Prediction> result : results) {
                try {
                    result.get();
                    saved++;
                }
                catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DomainException.class);
                    rejected.add((DomainException) e.getCause());
                }
            }

            assertThat(saved).isEqualTo(1);
            assertThat(rejected).hasSize(racers - 1).allSatisfy(e -> {
                assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT);
                assertThat(e.errorCode()).isEqualTo("DUPLICATE_PREDICTION");
            });
        }
        assertThat(mongoRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("updating an existing prediction is not mistaken for a duplicate")
    void updateOfTheSamePrediction_isNotADuplicate() {
        Prediction saved = adapter.save(newPrediction("user-1", "match-1", 2));

        Prediction updated = adapter.save(new Prediction(saved.id(), "user-1", "tournament-1", "match-1",
                PredictionType.EXACT_SCORE, 3, 0, DerivedOutcome.HOME_WIN,
                false, saved.createdAt(), Instant.now()));

        assertThat(updated.homeScore()).isEqualTo(3);
        assertThat(mongoRepo.count()).isEqualTo(1);
    }

    private static Prediction newPrediction(String userId, String matchId, int homeScore) {
        Instant now = Instant.now();
        return new Prediction(null, userId, "tournament-1", matchId,
                PredictionType.EXACT_SCORE, homeScore, 0, DerivedOutcome.from(homeScore, 0),
                false, now, now);
    }
}
