package com.scoregrid.tournament.tournament.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "tournament_participants")
@IdClass(ParticipantJpaEntity.ParticipantId.class)
public class ParticipantJpaEntity {

    @Id
    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Id
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    public ParticipantJpaEntity() {
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public static class ParticipantId implements Serializable {
        private Long tournamentId;
        private String userId;

        public ParticipantId() {
        }

        public ParticipantId(Long tournamentId, String userId) {
            this.tournamentId = tournamentId;
            this.userId = userId;
        }

        public Long getTournamentId() {
            return tournamentId;
        }

        public void setTournamentId(Long tournamentId) {
            this.tournamentId = tournamentId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParticipantId that)) return false;
            return tournamentId.equals(that.tournamentId) && userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return 31 * tournamentId.hashCode() + userId.hashCode();
        }
    }
}
