package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetUsersServiceTest {

    private InMemoryUserRepository users;
    private GetUsersService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        service = new GetUsersService(users);
    }

    @Test
    void findsAUserById() {
        User saved = users.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        assertThat(service.byId(saved.idAsString()).username()).isEqualTo("maxi");
    }

    @Test
    void missingUserIsNotFound() {
        assertThatThrownBy(() -> service.byId("999"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.kind()).isEqualTo(ErrorKind.NOT_FOUND);
                    assertThat(domain.errorCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    @DisplayName("a non-numeric id is a 404, not a 500")
    void nonNumericIdIsNotFound() {
        assertThatThrownBy(() -> service.byId("score-service"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    @Test
    void batchResolvesKnownIds() {
        User maxi = users.save(User.newAccount("maxi", "maxi@example.com", "hashed"));
        User ana = users.save(User.newAccount("ana", "ana@example.com", "hashed"));

        List<User> found = service.byIds(List.of(maxi.idAsString(), ana.idAsString()));

        assertThat(found).extracting(User::username).containsExactly("maxi", "ana");
    }

    @Test
    @DisplayName("unknown ids are omitted, not errors — one deleted user must not break a ranking")
    void batchOmitsUnknownIds() {
        User maxi = users.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        List<User> found = service.byIds(List.of(maxi.idAsString(), "404"));

        assertThat(found).extracting(User::username).containsExactly("maxi");
    }

    @Test
    void batchOmitsNonNumericIds() {
        User maxi = users.save(User.newAccount("maxi", "maxi@example.com", "hashed"));

        List<User> found = service.byIds(List.of("not-a-number", maxi.idAsString()));

        assertThat(found).extracting(User::username).containsExactly("maxi");
    }

    @Test
    void batchAcceptsExactlyTheLimit() {
        List<String> ids = IntStream.rangeClosed(1, GetUsersUseCase.MAX_BATCH_IDS)
                .mapToObj(String::valueOf)
                .toList();

        assertThat(service.byIds(ids)).isEmpty();
    }

    @Test
    void batchRejectsMoreThanTheLimit() {
        List<String> ids = IntStream.rangeClosed(1, GetUsersUseCase.MAX_BATCH_IDS + 1)
                .mapToObj(String::valueOf)
                .toList();

        assertThatThrownBy(() -> service.byIds(ids))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException domain = (DomainException) e;
                    assertThat(domain.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(domain.errorCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void listIsPaged() {
        IntStream.rangeClosed(1, 5).forEach(i ->
                users.save(User.newAccount("user" + i, "user" + i + "@example.com", "hashed")));

        PageResult<User> firstPage = service.list(0, 2);

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
    }
}
