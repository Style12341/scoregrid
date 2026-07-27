package com.scoregrid.auth.auth.domain.model;

import com.scoregrid.auth.shared.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("a new account is a PLAYER with no id")
    void newAccountIsAPlayer() {
        User user = User.newAccount("maxi", "maxi@example.com", "hashed");

        assertThat(user.id()).isNull();
        assertThat(user.idAsString()).isNull();
        assertThat(user.roles()).containsExactly(Role.PLAYER);
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("ids cross the boundary as strings")
    void idIsExposedAsString() {
        User user = User.newAccount("maxi", "maxi@example.com", "hashed").withId(42L);

        assertThat(user.idAsString()).isEqualTo("42");
    }

    @Test
    void withIdKeepsEverythingElse() {
        User user = User.newAccount("maxi", "maxi@example.com", "hashed").withId(7L);

        assertThat(user.username()).isEqualTo("maxi");
        assertThat(user.email()).isEqualTo("maxi@example.com");
        assertThat(user.passwordHash()).isEqualTo("hashed");
        assertThat(user.roles()).containsExactly(Role.PLAYER);
    }

    @ParameterizedTest(name = "username \"{0}\" is rejected")
    @ValueSource(strings = {"", "  ", "ab", "0123456789012345678901234567890"})
    void rejectsUsernameOutsideThreeToThirty(String username) {
        assertThatThrownBy(() -> User.newAccount(username, "maxi@example.com", "hashed"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("username");
    }

    @Test
    void acceptsUsernameAtBothBounds() {
        assertThat(User.newAccount("abc", "a@b.com", "h").username()).isEqualTo("abc");
        assertThat(User.newAccount("a".repeat(30), "a@b.com", "h").username()).hasSize(30);
    }

    @ParameterizedTest(name = "email \"{0}\" is rejected")
    @ValueSource(strings = {"nope", "@example.com", "maxi@", ""})
    void rejectsStructurallyInvalidEmail(String email) {
        assertThatThrownBy(() -> User.newAccount("maxi", email, "hashed"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThatThrownBy(() -> User.newAccount("maxi", "maxi@example.com", " "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    @DisplayName("roles are not modifiable through the returned set")
    void rolesAreUnmodifiable() {
        User user = new User(1L, "maxi", "maxi@example.com", "hashed", Set.of(Role.PLAYER));

        assertThatThrownBy(() -> user.roles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unknownRoleNameIsADomainFailure() {
        assertThatThrownBy(() -> Role.of("SUPERUSER"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("SUPERUSER");
    }
}
