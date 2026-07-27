package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class GetUsersService implements GetUsersUseCase {

    private final UserRepositoryPort users;

    GetUsersService(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public User byId(String id) {
        return numericId(id)
                .flatMap(users::findById)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "No user with id " + id + "."));
    }

    @Override
    public List<User> byIds(List<String> ids) {
        if (ids.size() > MAX_BATCH_IDS) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "At most " + MAX_BATCH_IDS + " ids per call; got " + ids.size() + ".");
        }

        List<Long> numeric = ids.stream()
                .map(GetUsersService::numericId)
                .flatMap(Optional::stream)
                .distinct()
                .toList();

        if (numeric.isEmpty()) {
            return List.of();
        }
        return users.findAllByIds(numeric);
    }

    @Override
    public PageResult<User> list(int page, int size) {
        return users.findAll(page, size);
    }

    /**
     * Empty rather than throwing for a non-numeric id: batch lookup treats an
     * unresolvable id as absent, and {@link #byId} turns the empty into a 404.
     */
    private static Optional<Long> numericId(String id) {
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
