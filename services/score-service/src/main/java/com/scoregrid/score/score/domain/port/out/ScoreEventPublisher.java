package com.scoregrid.score.score.domain.port.out;

import com.scoregrid.score.score.domain.model.MatchScore;

public interface ScoreEventPublisher {

    void scoreCalculated(MatchScore matchScore);
}
