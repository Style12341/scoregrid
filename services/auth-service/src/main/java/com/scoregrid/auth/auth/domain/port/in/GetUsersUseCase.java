package com.scoregrid.auth.auth.domain.port.in;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.User;

import java.util.List;

public interface GetUsersUseCase {

    /** @throws com.scoregrid.auth.shared.error.DomainException {@code NOT_FOUND} */
    User byId(String id);

    /**
     * Bulk lookup for Score Service, which attaches usernames to rankings.
     *
     * <p>Unknown ids are omitted rather than raising — a deleted user must not
     * break a whole ranking page. Ids that are not numeric are omitted for the
     * same reason.
     *
     * @throws com.scoregrid.auth.shared.error.DomainException {@code VALIDATION_FAILED}
     *         above {@value #MAX_BATCH_IDS} ids.
     */
    List<User> byIds(List<String> ids);

    PageResult<User> list(int page, int size);

    int MAX_BATCH_IDS = 200;
}
