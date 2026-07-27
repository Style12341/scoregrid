package com.scoregrid.score.score.infrastructure.persistence;

import org.springframework.data.mongodb.core.mapping.Field;

class ScoredPredictionEmbedded {

    @Field("userId")
    private String userId;

    @Field("predictionId")
    private String predictionId;

    @Field("predictedHomeScore")
    private int predictedHomeScore;

    @Field("predictedAwayScore")
    private int predictedAwayScore;

    @Field("points")
    private int points;

    @Field("hit")
    private boolean hit;

    @Field("exactHit")
    private boolean exactHit;

    ScoredPredictionEmbedded() {
    }

    ScoredPredictionEmbedded(String userId, String predictionId, int predictedHomeScore,
                             int predictedAwayScore, int points, boolean hit, boolean exactHit) {
        this.userId = userId;
        this.predictionId = predictionId;
        this.predictedHomeScore = predictedHomeScore;
        this.predictedAwayScore = predictedAwayScore;
        this.points = points;
        this.hit = hit;
        this.exactHit = exactHit;
    }

    public String getUserId() { return userId; }
    void setUserId(String userId) { this.userId = userId; }

    public String getPredictionId() { return predictionId; }
    void setPredictionId(String predictionId) { this.predictionId = predictionId; }

    public int getPredictedHomeScore() { return predictedHomeScore; }
    void setPredictedHomeScore(int predictedHomeScore) { this.predictedHomeScore = predictedHomeScore; }

    public int getPredictedAwayScore() { return predictedAwayScore; }
    void setPredictedAwayScore(int predictedAwayScore) { this.predictedAwayScore = predictedAwayScore; }

    public int getPoints() { return points; }
    void setPoints(int points) { this.points = points; }

    public boolean isHit() { return hit; }
    void setHit(boolean hit) { this.hit = hit; }

    public boolean isExactHit() { return exactHit; }
    void setExactHit(boolean exactHit) { this.exactHit = exactHit; }
}
