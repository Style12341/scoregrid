package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.Authentication;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.LoginCommand;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AuthenticateUserServiceTest {

    private InMemoryUserRepository users;
    private FakePasswordHasher passwordHasher;
    private FakeTokenIssuer tokenIssuer;
    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        passwordHasher = new FakePasswordHasher();
        tokenIssuer = new FakeTokenIssuer();
        service = new AuthenticateUserService(users, passwordHasher, tokenIssuer);

        users.save(User.newAccount("maxi", "maxi@example.com", FakePasswordHasher.hashOf("correct-horse")));
    }

    @Test
    void authenticatesByUsername() {
        Authentication result = service.authenticate(new LoginCommand("maxi", "correct-horse"));

        assertThat(result.token().value()).isEqualTo("token-for-maxi");
        assertThat(result.token().expiresAt()).isEqualTo(FakeTokenIssuer.EXPIRES_AT);
        assertThat(result.user().username()).isEqualTo("maxi");
    }

    @Test
    void authenticatesByEmail() {
        Authentication result = service.authenticate(new LoginCommand("maxi@example.com", "correct-horse"));

        assertThat(result.user().username()).isEqualTo("maxi");
    }

    @Test
    void issuesTheTokenForTheAuthenticatedUser() {
        service.authenticate(new LoginCommand("maxi", "correct-horse"));

        assertThat(tokenIssuer.lastSubject().username()).isEqualTo("maxi");
    }

    @Test
    void rejectsAWrongPassword() {
        assertThatThrownBy(() -> service.authenticate(new LoginCommand("maxi", "wrong")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.UNAUTHORIZED));
    }

    @Test
    void rejectsAnUnknownAccount() {
        assertThatThrownBy(() -> service.authenticate(new LoginCommand("nobody", "correct-horse")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.UNAUTHORIZED));
    }

    @Test
    @DisplayName("an unknown account and a wrong password are indistinguishable")
    void bothFailuresLookIdentical() {
        DomainException unknownAccount = catchThrowableOfType(DomainException.class,
                () -> service.authenticate(new LoginCommand("nobody", "correct-horse")));
        DomainException wrongPassword = catchThrowableOfType(DomainException.class,
                () -> service.authenticate(new LoginCommand("maxi", "wrong")));

        assertThat(unknownAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknownAccount.errorCode()).isEqualTo(wrongPassword.errorCode());
        assertThat(unknownAccount.kind()).isEqualTo(wrongPassword.kind());
    }

    @Test
    @DisplayName("an unknown account still spends hashing time, so latency does not leak existence")
    void equalisesTimingForAnUnknownAccount() {
        assertThat(passwordHasher.burnCount()).isZero();

        assertThatThrownBy(() -> service.authenticate(new LoginCommand("nobody", "correct-horse")))
                .isInstanceOf(DomainException.class);

        assertThat(passwordHasher.burnCount()).isOne();
    }

    @Test
    void noTokenIsIssuedOnFailure() {
        assertThatThrownBy(() -> service.authenticate(new LoginCommand("maxi", "wrong")))
                .isInstanceOf(DomainException.class);

        assertThat(tokenIssuer.lastSubject()).isNull();
    }
}
