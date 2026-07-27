package com.scoregrid.tournament.match.infrastructure.persistence;

import com.scoregrid.tournament.team.infrastructure.persistence.TeamEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "matches")
public class MatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "phase_id")
    private Long phaseId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "home_team_id")
    private TeamEntity homeTeam;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "away_team_id")
    private TeamEntity awayTeam;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    public MatchEntity() {}

    public Long getId() { return id; }
    public Long getTournamentId() { return tournamentId; }
    public Long getGroupId() { return groupId; }
    public Long getPhaseId() { return phaseId; }
    public TeamEntity getHomeTeam() { return homeTeam; }
    public TeamEntity getAwayTeam() { return awayTeam; }
    public Instant getStartTime() { return startTime; }
    public String getStatus() { return status; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public void setStatus(String status) { this.status = status; }
}
