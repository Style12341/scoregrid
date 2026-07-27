package com.scoregrid.auth.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The stored shape of a user. Not a domain model — see
 * {@code com.scoregrid.auth.auth.domain.model.User}, and AGENTS.md section 6.
 *
 * <p>Column definitions must match {@code V1__create_users_roles.sql} exactly:
 * {@code ddl-auto} is {@code validate}, so drift fails at startup.
 *
 * <p>Deliberately {@code @Getter}/{@code @Setter} and not {@code @Data}: on a
 * JPA entity, Lombok's generated {@code equals}/{@code hashCode} walks the
 * {@code roles} association, which forces the collection to load and makes
 * identity depend on mutable state. {@code @ToString} has the same problem.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash. The raw password is never stored, logged or returned. */
    @Column(nullable = false)
    private String password;

    // EAGER because every read of a user needs its roles: they go into the JWT
    // on login and into the profile response everywhere else. A lazy collection
    // here buys a LazyInitializationException, not a saved query.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new LinkedHashSet<>();
}
