package com.scoregrid.tournament.tournament.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentJpaRepository extends JpaRepository<TournamentJpaEntity, Long> {

    List<TournamentJpaEntity> findAllByStatus(String status);

    long countByStatus(String status);
}
