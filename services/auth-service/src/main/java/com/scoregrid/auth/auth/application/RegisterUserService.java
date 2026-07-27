package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.RegisterUserUseCase;
import com.scoregrid.auth.auth.domain.port.out.PasswordHasher;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.springframework.stereotype.Service;

@Service
class RegisterUserService implements RegisterUserUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasher passwordHasher;

    RegisterUserService(UserRepositoryPort users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public User register(RegisterCommand command) {
        // A courtesy check for the common case. It is NOT the rule: two
        // simultaneous registrations both pass this and the unique index
        // decides, which the adapter translates back into DUPLICATE_USER.
        if (users.existsByUsernameOrEmail(command.username(), command.email())) {
            throw duplicate();
        }

        User account = User.newAccount(
                command.username(),
                command.email(),
                passwordHasher.hash(command.rawPassword()));

        return users.save(account);
    }

    static DomainException duplicate() {
        return new DomainException(ErrorKind.CONFLICT, "DUPLICATE_USER",
                "That username or email is already registered.");
    }
}
