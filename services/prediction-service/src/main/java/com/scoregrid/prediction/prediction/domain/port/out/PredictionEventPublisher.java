package com.scoregrid.prediction.prediction.domain.port.out;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

public interface PredictionEventPublisher {

    void predictionCreated(Prediction prediction);

    void predictionUpdated(Prediction prediction);
}
