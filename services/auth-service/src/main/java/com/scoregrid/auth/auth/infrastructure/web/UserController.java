package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** docs/contracts.md#auth-service — /api/users. */
@RestController
@RequestMapping("/api/users")
class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final GetUsersUseCase getUsers;

    UserController(GetUsersUseCase getUsers) {
        this.getUsers = getUsers;
    }

    /**
     * Bulk lookup used by Score Service to attach usernames to rankings.
     *
     * <p>Declared before {@code /{id}} for readability only — Spring already
     * prefers the literal segment over the template, so "batch" is never
     * mistaken for an id.
     *
     * <p>Unknown ids are omitted rather than raising: one deleted account must
     * not take down a whole ranking page.
     */
    @GetMapping("/batch")
    List<UserSummaryResponse> batch(@RequestParam("ids") String ids) {
        List<String> requested = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();

        return getUsers.byIds(requested).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    /** Public profile: id and username, never the email. */
    @GetMapping("/{id}")
    UserSummaryResponse byId(@PathVariable String id) {
        return UserSummaryResponse.from(getUsers.byId(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<UserProfileResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "page must not be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        return PageResponse.from(getUsers.list(page, size), UserProfileResponse::from);
    }
}
