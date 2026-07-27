package com.scoregrid.tournament.team.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamJpaRepository extends JpaRepository<TeamJpaEntity, Long> {
}
