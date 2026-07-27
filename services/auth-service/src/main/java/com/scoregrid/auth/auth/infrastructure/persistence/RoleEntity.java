package com.scoregrid.auth.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A role row. Seeded by {@code V1__create_users_roles.sql} with PLAYER and
 * ADMIN; the application never inserts one.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
