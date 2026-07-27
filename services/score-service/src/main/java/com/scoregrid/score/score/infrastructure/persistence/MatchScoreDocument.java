package com.scoregrid.score.score.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "match_scores")
class MatchScoreDocument {

    @Id
    private String matchId;

    @Field("tournamentId")
    private String tournamentId;

    @Field("homeScore")
    private int homeScore;

    @Field("awayScore")
    private int awayScore;

    @Field("outcome")
    private String outcome;

    @Field("scoredPredictions")
    private int scoredPredictions;

    @Field("totalPointsAwarded")
    private int totalPointsAwarded;

    @Field("calculatedAt")
    private Instant calculatedAt;

    @Field("individualScores")
    private List<ScoredPredictionEmbedded> individualScores;

    MatchScoreDocument() {
    }

    MatchScoreDocument(String matchId, String tournamentId, int homeScore, int awayScore,
                       String outcome, int scoredPredictions, int totalPointsAwarded,
                       Instant calculatedAt, List<ScoredPredictionEmbedded> individualScores) {
        this.matchId = matchId;
        this.tournamentId = tournamentId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.outcome = outcome;
        this.scoredPredictions = scoredPredictions;
        this.totalPointsAwarded = totalPointsAwarded;
        this.calculatedAt = calculatedAt;
        this.individualScores = individualScores;
    }

    public String getMatchId() { return matchId; }
    void setMatchId(String matchId) { this.matchId = matchId; }

    public String getTournamentId() { return tournamentId; }
    void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    public int getHomeScore() { return homeScore; }
    void setHomeScore(int homeScore) { this.homeScore = homeScore; }

    public int getAwayScore() { return awayScore; }
    void setAwayScore(int awayScore) { this.awayScore = awayScore; }

    public String getOutcome() { return outcome; }
    void setOutcome(String outcome) { this.outcome = outcome; }

    public int getScoredPredictions() { return scoredPredictions; }
    void setScoredPredictions(int scoredPredictions) { this.scoredPredictions = scoredPredictions; }

    public int getTotalPointsAwarded() { return totalPointsAwarded; }
    void setTotalPointsAwarded(int totalPointsAwarded) { this.totalPointsAwarded = totalPointsAwarded; }

    public Instant getCalculatedAt() { return calculatedAt; }
    void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public List<ScoredPredictionEmbedded> getIndividualScores() { return individualScores; }
    void setIndividualScores(List<ScoredPredictionEmbedded> individualScores) {
        this.individualScores = individualScores;
    }
}
