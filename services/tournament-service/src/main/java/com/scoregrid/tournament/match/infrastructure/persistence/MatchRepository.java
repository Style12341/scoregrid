package com.scoregrid.tournament.match.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {
}
