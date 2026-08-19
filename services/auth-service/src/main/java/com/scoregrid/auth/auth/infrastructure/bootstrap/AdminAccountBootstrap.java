package com.scoregrid.auth.auth.infrastructure.bootstrap;

import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.out.PasswordHasher;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Creates the configured initial administrator after Flyway has prepared the database. */
@Component
class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final Set<Role> ADMIN_ROLES = Set.of(Role.PLAYER, Role.ADMIN);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepositoryPort users;
    private final PasswordHasher passwordHasher;
    private final String username;
    private final String email;
    private final String password;

    AdminAccountBootstrap(
            UserRepositoryPort users,
            PasswordHasher passwordHasher,
            @Value("${scoregrid.admin.username:admin}") String username,
            @Value("${scoregrid.admin.email:admin@scoregrid.local}") String email,
            @Value("${scoregrid.admin.password:}") String password) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        User existingByUsername = users.findByUsernameOrEmail(username).orElse(null);
        if (existingByUsername != null) {
            ensureAdminRole(existingByUsername);
            return;
        }

        User existingByEmail = users.findByUsernameOrEmail(email).orElse(null);
        if (existingByEmail != null) {
            throw new IllegalStateException(
                    "scoregrid.admin.email belongs to another username: " + existingByEmail.username());
        }

        validatePassword();
        users.save(User.newAdminAccount(username, email, passwordHasher.hash(password)));
        log.info("Created initial admin account with username '{}'.", username);
    }

    private void ensureAdminRole(User existing) {
        if (existing.roles().containsAll(ADMIN_ROLES)) {
            return;
        }

        // Keep the existing password and identity. Bootstrap only grants the
        // roles needed by an administrator, including PLAYER for player flows.
        users.save(new User(existing.id(), existing.username(), existing.email(),
                existing.passwordHash(), ADMIN_ROLES));
        log.info("Granted initial admin roles to existing account '{}'.", existing.username());
    }

    private void validatePassword() {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "SCOREGRID_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }
}
