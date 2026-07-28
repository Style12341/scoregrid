package com.scoregrid.tournament.team.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "tournament_teams")
@IdClass(TournamentTeamJpaEntity.TournamentTeamId.class)
public class TournamentTeamJpaEntity {

    @Id
    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Id
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    public TournamentTeamJpaEntity() {
    }

    public TournamentTeamJpaEntity(Long tournamentId, Long teamId) {
        this.tournamentId = tournamentId;
        this.teamId = teamId;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public static class TournamentTeamId implements Serializable {
        private Long tournamentId;
        private Long teamId;

        public TournamentTeamId() {
        }

        public TournamentTeamId(Long tournamentId, Long teamId) {
            this.tournamentId = tournamentId;
            this.teamId = teamId;
        }

        public Long getTournamentId() {
            return tournamentId;
        }

        public void setTournamentId(Long tournamentId) {
            this.tournamentId = tournamentId;
        }

        public Long getTeamId() {
            return teamId;
        }

        public void setTeamId(Long teamId) {
            this.teamId = teamId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TournamentTeamId that)) return false;
            return tournamentId.equals(that.tournamentId) && teamId.equals(that.teamId);
        }

        @Override
        public int hashCode() {
            return 31 * tournamentId.hashCode() + teamId.hashCode();
        }
    }
}
