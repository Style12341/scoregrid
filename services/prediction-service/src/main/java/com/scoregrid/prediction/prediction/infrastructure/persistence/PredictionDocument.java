package com.scoregrid.prediction.prediction.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "predictions")
@CompoundIndex(def = "{'userId': 1, 'matchId': 1}", unique = true)
class PredictionDocument {

    @Id
    private String id;

    @Field("userId")
    private String userId;

    @Field("tournamentId")
    private String tournamentId;

    @Field("matchId")
    private String matchId;

    @Field("predictionType")
    private String predictionType;

    @Field("homeScore")
    private int homeScore;

    @Field("awayScore")
    private int awayScore;

    @Field("derivedOutcome")
    private String derivedOutcome;

    @Field("locked")
    private boolean locked;

    @Field("createdAt")
    private Instant createdAt;

    @Field("updatedAt")
    private Instant updatedAt;

    PredictionDocument() {
    }

    PredictionDocument(String id, String userId, String tournamentId, String matchId,
                        String predictionType, int homeScore, int awayScore,
                        String derivedOutcome, boolean locked,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.tournamentId = tournamentId;
        this.matchId = matchId;
        this.predictionType = predictionType;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.derivedOutcome = derivedOutcome;
        this.locked = locked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    void setUserId(String userId) { this.userId = userId; }

    public String getTournamentId() { return tournamentId; }
    void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    public String getMatchId() { return matchId; }
    void setMatchId(String matchId) { this.matchId = matchId; }

    public String getPredictionType() { return predictionType; }
    void setPredictionType(String predictionType) { this.predictionType = predictionType; }

    public int getHomeScore() { return homeScore; }
    void setHomeScore(int homeScore) { this.homeScore = homeScore; }

    public int getAwayScore() { return awayScore; }
    void setAwayScore(int awayScore) { this.awayScore = awayScore; }

    public String getDerivedOutcome() { return derivedOutcome; }
    void setDerivedOutcome(String derivedOutcome) { this.derivedOutcome = derivedOutcome; }

    public boolean isLocked() { return locked; }
    void setLocked(boolean locked) { this.locked = locked; }

    public Instant getCreatedAt() { return createdAt; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
