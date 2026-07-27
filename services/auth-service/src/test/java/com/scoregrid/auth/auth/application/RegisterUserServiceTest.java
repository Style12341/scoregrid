package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.RegisterUserUseCase.RegisterCommand;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserServiceTest {

    private InMemoryUserRepository users;
    private FakePasswordHasher passwordHasher;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        passwordHasher = new FakePasswordHasher();
        service = new RegisterUserService(users, passwordHasher);
    }

    @Test
    void registersAPlayerAndAssignsAnId() {
        User created = service.register(new RegisterCommand("maxi", "maxi@example.com", "correct-horse"));

        assertThat(created.id()).isNotNull();
        assertThat(created.username()).isEqualTo("maxi");
        assertThat(created.roles()).containsExactly(Role.PLAYER);
    }

    @Test
    @DisplayName("the raw password is never stored")
    void storesOnlyTheHash() {
        User created = service.register(new RegisterCommand("maxi", "maxi@example.com", "correct-horse"));

        assertThat(created.passwordHash())
                .isEqualTo(FakePasswordHasher.hashOf("correct-horse"))
                .isNotEqualTo("correct-horse");
    }

    @Test
    void rejectsATakenUsername() {
        service.register(new RegisterCommand("maxi", "maxi@example.com", "correct-horse"));

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("maxi", "other@example.com", "another-password")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(domain.errorCode()).isEqualTo("DUPLICATE_USER");
                });
    }

    @Test
    void rejectsATakenEmail() {
        service.register(new RegisterCommand("maxi", "maxi@example.com", "correct-horse"));

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ana", "maxi@example.com", "another-password")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("an invalid username is rejected before anything is hashed or saved")
    void doesNotPersistAnInvalidAccount() {
        assertThatThrownBy(() -> service.register(new RegisterCommand("ab", "a@b.com", "password1")))
                .isInstanceOf(DomainException.class);

        assertThat(users.findByUsernameOrEmail("ab")).isEmpty();
    }
}
