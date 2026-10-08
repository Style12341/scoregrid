package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase;
import com.scoregrid.auth.auth.domain.port.out.PasswordHasher;
import com.scoregrid.auth.auth.domain.port.out.TokenIssuer;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
class AuthenticateUserService implements AuthenticateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateUserService.class);

    private final UserRepositoryPort users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    AuthenticateUserService(UserRepositoryPort users,
                            PasswordHasher passwordHasher,
                            TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public Authentication authenticate(LoginCommand command) {
        Optional<User> found = users.findByUsernameOrEmail(command.usernameOrEmail());

        if (found.isEmpty()) {
            // Hash anyway. Returning early here would make an unknown username
            // measurably faster than a wrong password, which hands an attacker
            // the account enumeration the identical error message denies them.
            passwordHasher.burnComparableTime();
            throw loginFailed(command);
        }

        User user = found.get();
        if (!passwordHasher.matches(command.rawPassword(), user.passwordHash())) {
            throw loginFailed(command);
        }

        log.info("Login succeeded: userId={}", user.id());
        return new Authentication(tokenIssuer.issue(user), user);
    }

    /**
     * Logs the failed login and returns the error to throw. One message for
     * both failures, deliberately — docs/contracts.md#auth-service. The log
     * line does not tell them apart either, and never holds the password.
     */
    private static DomainException loginFailed(LoginCommand command) {
        log.warn("Login failed: usernameOrEmail={}", command.usernameOrEmail());
        return new DomainException(ErrorKind.UNAUTHORIZED, "UNAUTHORIZED",
                "Invalid credentials.");
    }
}
