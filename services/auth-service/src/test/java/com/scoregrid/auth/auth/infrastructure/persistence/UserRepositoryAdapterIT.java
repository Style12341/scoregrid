package com.scoregrid.auth.auth.infrastructure.persistence;

import com.scoregrid.auth.TestcontainersConfiguration;
import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a real PostgreSQL, not H2.
 *
 * <p>The point is not only the adapter. This is what proves
 * {@code V1__create_users_roles.sql} and {@code UserEntity} still agree:
 * {@code ddl-auto} is {@code validate}, so a column that drifted from the
 * migration fails this test at context startup rather than in production.
 * H2 would not catch it — the unique-violation behaviour below is
 * PostgreSQL's, and it is the actual duplicate rule.
 *
 * <p>Requires Docker. See AGENTS.md section 7.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserRepositoryAdapter.class, UserMapperImpl.class})
class UserRepositoryAdapterIT {

    @Autowired
    private UserRepositoryAdapter adapter;

    @Autowired
    private RoleJpaRepository roles;

    @Test
    @DisplayName("the migration seeds PLAYER and ADMIN")
    void rolesAreSeeded() {
        assertThat(roles.findByName("PLAYER")).isPresent();
        assertThat(roles.findByName("ADMIN")).isPresent();
    }

    @Test
    void savesAndReadsBackAUser() {
        User saved = adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        assertThat(saved.id()).isNotNull();

        Optional<User> found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("maxi");
        assertThat(found.get().email()).isEqualTo("maxi@example.com");
        assertThat(found.get().passwordHash()).isEqualTo("hashed");
        assertThat(found.get().roles()).containsExactly(Role.PLAYER);
    }

    @Test
    @DisplayName("the unique index is the duplicate rule, not a read-then-write check")
    void duplicateUsernameHitsTheIndex() {
        adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        // Straight to save, deliberately skipping existsByUsernameOrEmail: this
        // is the path two concurrent registrations take when both pre-checks pass.
        assertThatThrownBy(() -> adapter.save(User.newAccount("maxi", "other@example.com", "hashed")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.kind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(domain.errorCode()).isEqualTo("DUPLICATE_USER");
                });
    }

    @Test
    void duplicateEmailHitsTheIndex() {
        adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        assertThatThrownBy(() -> adapter.save(User.newAccount("ana", "maxi@example.com", "hashed")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode()).isEqualTo("DUPLICATE_USER"));
    }

    @Test
    void findsByUsernameOrEmailWithOneValue() {
        adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        assertThat(adapter.findByUsernameOrEmail("maxi")).isPresent();
        assertThat(adapter.findByUsernameOrEmail("maxi@example.com")).isPresent();
        assertThat(adapter.findByUsernameOrEmail("nobody")).isEmpty();
    }

    @Test
    void batchLookupReturnsOnlyTheIdsThatExist() {
        User maxi = adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));
        User ana = adapter.save(User.newAccount("ana", "ana@example.com", "hashed"));

        List<User> found = adapter.findAllByIds(List.of(maxi.id(), ana.id(), 9999L));

        assertThat(found).extracting(User::username).containsExactlyInAnyOrder("maxi", "ana");
    }

    @Test
    void existsChecksBothColumns() {
        adapter.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        assertThat(adapter.existsByUsernameOrEmail("maxi", "other@example.com")).isTrue();
        assertThat(adapter.existsByUsernameOrEmail("other", "maxi@example.com")).isTrue();
        assertThat(adapter.existsByUsernameOrEmail("other", "other@example.com")).isFalse();
    }

    @Test
    void pagesInAStableOrder() {
        for (int i = 1; i <= 5; i++) {
            adapter.save(User.newAccount("user" + i, "user" + i + "@example.com", "hashed"));
        }

        PageResult<User> first = adapter.findAll(0, 2);
        PageResult<User> second = adapter.findAll(1, 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        // Sorted by id, so pages do not overlap or repeat between requests.
        assertThat(first.items()).extracting(User::username).containsExactly("user1", "user2");
        assertThat(second.items()).extracting(User::username).containsExactly("user3", "user4");
    }

    @Test
    @DisplayName("a saved user keeps exactly the roles it was given")
    void rolesRoundTrip() {
        User admin = adapter.save(new User(null, "boss", "boss@example.com", "hashed",
                Set.of(Role.PLAYER, Role.ADMIN)));

        assertThat(adapter.findById(admin.id()))
                .get()
                .satisfies(u -> assertThat(u.roles()).containsExactlyInAnyOrder(Role.PLAYER, Role.ADMIN));
    }
}
