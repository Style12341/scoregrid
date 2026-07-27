package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;

import java.util.List;

/**
 * The full profile of the acting user. Returned by {@code /api/auth/me},
 * {@code /api/auth/register} and the admin listing.
 *
 * <p>Carries the email, so it is only ever sent to the user themselves or to an
 * ADMIN. Other users' profiles go out as {@link UserSummaryResponse}.
 */
record UserProfileResponse(String id, String username, String email, List<String> roles) {

    static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.idAsString(),
                user.username(),
                user.email(),
                user.roles().stream().map(Role::name).toList());
    }
}
