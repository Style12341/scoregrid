package com.scoregrid.auth.auth.domain.port.in;

import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.User;

public interface AuthenticateUserUseCase {

    /**
     * Exchanges credentials for a signed token.
     *
     * @throws com.scoregrid.auth.shared.error.DomainException {@code UNAUTHORIZED}
     *         for both an unknown account and a wrong password. The two cases must
     *         stay indistinguishable — docs/contracts.md#auth-service.
     */
    Authentication authenticate(LoginCommand command);

    record LoginCommand(String usernameOrEmail, String rawPassword) {
    }

    record Authentication(IssuedToken token, User user) {
    }
}
