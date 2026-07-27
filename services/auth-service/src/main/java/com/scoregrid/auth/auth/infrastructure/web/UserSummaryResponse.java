package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.User;

/**
 * A user as everyone else is allowed to see them: id and display name.
 *
 * <p>No email. Any authenticated caller can look up any id — Score Service does
 * exactly that to label rankings — so this shape must stay safe to hand out.
 */
record UserSummaryResponse(String id, String username) {

    static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.idAsString(), user.username());
    }
}
