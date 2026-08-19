package com.scoregrid.auth.auth.domain.model;

import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A ScoreGrid account.
 *
 * <p>Framework-free by design: no JPA, no Jackson. {@code UserEntity} in the
 * persistence package is the stored shape and maps to this; the two are allowed
 * to drift, which is the whole point of keeping them apart.
 *
 * <p>Holds the password <em>hash</em> only. A raw password never reaches this
 * class — hashing happens in the {@code PasswordHasher} port before construction.
 */
public final class User {

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 30;

    private final Long id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Set<Role> roles;

    public User(Long id, String username, String email, String passwordHash, Set<Role> roles) {
        this.id = id;
        this.username = requireUsername(username);
        this.email = requireEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    /** A brand-new account: no id yet, {@code PLAYER} by default per the contract. */
    public static User newAccount(String username, String email, String passwordHash) {
        return new User(null, username, email, passwordHash, Set.of(Role.PLAYER));
    }

    /** The configured bootstrap account can participate as well as administer. */
    public static User newAdminAccount(String username, String email, String passwordHash) {
        return new User(null, username, email, passwordHash, Set.of(Role.PLAYER, Role.ADMIN));
    }

    public User withId(Long assignedId) {
        return new User(assignedId, username, email, passwordHash, roles);
    }

    public Long id() {
        return id;
    }

    /** Ids cross service boundaries as strings — docs/contracts.md#conventions. */
    public String idAsString() {
        return id == null ? null : id.toString();
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return roles;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    private static String requireUsername(String value) {
        String username = requireText(value, "username");
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters.");
        }
        return username;
    }

    private static String requireEmail(String value) {
        String email = requireText(value, "email");
        // Deliberately shallow. Bean validation already ran at the edge; this
        // only stops something structurally absurd reaching the database.
        if (email.indexOf('@') <= 0 || email.indexOf('@') == email.length() - 1) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "email is not a valid address.");
        }
        return email;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    field + " must not be blank.");
        }
        return value;
    }
}
