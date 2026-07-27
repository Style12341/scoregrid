package com.scoregrid.tournament.tournament.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentJpaRepository extends JpaRepository<TournamentJpaEntity, Long> {

    Page<TournamentJpaEntity> findAllByStatus(String status, Pageable pageable);

    long countByStatus(String status);
}
