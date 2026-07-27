package com.scoregrid.auth.auth.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsernameOrEmail(String username, String email);

    List<UserEntity> findAllByIdIn(List<Long> ids);

    boolean existsByUsernameOrEmail(String username, String email);

    @Override
    Page<UserEntity> findAll(Pageable pageable);
}
