package com.scoregrid.tournament.group.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupTeamJpaRepository extends JpaRepository<GroupTeamJpaEntity, GroupTeamJpaEntity.GroupTeamId> {

    List<GroupTeamJpaEntity> findByGroupId(Long groupId);

    boolean existsByGroupIdAndTeamId(Long groupId, Long teamId);

    /**
     * Find the group a team belongs to within a specific tournament.
     * Used to enforce "one group per team per tournament".
     */
    @Query("""
        SELECT gt.groupId FROM GroupTeamJpaEntity gt
        JOIN GroupJpaEntity g ON g.id = gt.groupId
        WHERE gt.teamId = :teamId AND g.tournamentId = :tournamentId
        """)
    Optional<Long> findGroupIdByTeamIdAndTournamentId(Long teamId, Long tournamentId);
}
