package com.scoregrid.auth.auth.domain.port.in;

import com.scoregrid.auth.auth.domain.model.User;

public interface RegisterUserUseCase {

    /**
     * Creates a {@code PLAYER} account.
     *
     * @throws com.scoregrid.auth.shared.error.DomainException {@code DUPLICATE_USER}
     *         when the username or email is taken.
     */
    User register(RegisterCommand command);

    record RegisterCommand(String username, String email, String rawPassword) {
    }
}
