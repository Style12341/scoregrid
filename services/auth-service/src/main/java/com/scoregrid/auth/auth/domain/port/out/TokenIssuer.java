package com.scoregrid.auth.auth.domain.port.out;

import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.User;

public interface TokenIssuer {

    /** Signs the claim set from docs/contracts.md#authentication-contract. */
    IssuedToken issue(User user);
}
