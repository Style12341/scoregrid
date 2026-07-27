package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.out.TokenIssuer;

import java.time.Instant;

class FakeTokenIssuer implements TokenIssuer {

    static final Instant EXPIRES_AT = Instant.parse("2026-08-01T00:00:00Z");

    private User lastSubject;

    @Override
    public IssuedToken issue(User user) {
        this.lastSubject = user;
        return new IssuedToken("token-for-" + user.username(), EXPIRES_AT);
    }

    User lastSubject() {
        return lastSubject;
    }
}
