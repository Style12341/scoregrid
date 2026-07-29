package com.scoregrid.tournament.group.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "group_teams")
@IdClass(GroupTeamJpaEntity.GroupTeamId.class)
public class GroupTeamJpaEntity {

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    public GroupTeamJpaEntity() {
    }

    public GroupTeamJpaEntity(Long groupId, Long teamId) {
        this.groupId = groupId;
        this.teamId = teamId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public static class GroupTeamId implements Serializable {
        private Long groupId;
        private Long teamId;

        public GroupTeamId() {
        }

        public GroupTeamId(Long groupId, Long teamId) {
            this.groupId = groupId;
            this.teamId = teamId;
        }

        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
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
            if (!(o instanceof GroupTeamId that)) return false;
            return groupId.equals(that.groupId) && teamId.equals(that.teamId);
        }

        @Override
        public int hashCode() {
            return 31 * groupId.hashCode() + teamId.hashCode();
        }
    }
}
