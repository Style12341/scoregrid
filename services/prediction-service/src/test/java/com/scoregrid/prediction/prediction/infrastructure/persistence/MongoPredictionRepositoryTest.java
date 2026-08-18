package com.scoregrid.prediction.prediction.infrastructure.persistence;

import com.mongodb.MongoWriteException;
import com.scoregrid.prediction.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Import(TestcontainersConfiguration.class)
class MongoPredictionRepositoryTest {

    @Autowired
    private MongoPredictionRepository repository;

    @BeforeEach
    void cleanCollection() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("unique index on (userId, matchId) rejects duplicate")
    void uniqueIndexRejectsDuplicate() {
        var doc = new PredictionDocument(
                null, "user-1", "tournament-1", "match-1",
                "EXACT_SCORE", 2, 1, "HOME_WIN", false,
                Instant.now(), Instant.now());
        repository.save(doc);

        var duplicate = new PredictionDocument(
                null, "user-1", "tournament-1", "match-1",
                "EXACT_SCORE", 1, 0, "HOME_WIN", false,
                Instant.now(), Instant.now());

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DuplicateKeyException.class)
                .hasRootCauseInstanceOf(MongoWriteException.class)
                .hasMessageContaining("E11000");
    }

    @Test
    @DisplayName("different user or match does not trigger the index")
    void differentUserOrMatchDoesNotConflict() {
        var doc = new PredictionDocument(
                null, "user-1", "tournament-1", "match-1",
                "EXACT_SCORE", 2, 1, "HOME_WIN", false,
                Instant.now(), Instant.now());
        repository.save(doc);

        var differentUser = new PredictionDocument(
                null, "user-2", "tournament-1", "match-1",
                "EXACT_SCORE", 1, 0, "HOME_WIN", false,
                Instant.now(), Instant.now());
        repository.save(differentUser);

        var differentMatch = new PredictionDocument(
                null, "user-1", "tournament-1", "match-2",
                "EXACT_SCORE", 1, 0, "HOME_WIN", false,
                Instant.now(), Instant.now());
        repository.save(differentMatch);

        assertThat(repository.count()).isEqualTo(3);
    }
}
