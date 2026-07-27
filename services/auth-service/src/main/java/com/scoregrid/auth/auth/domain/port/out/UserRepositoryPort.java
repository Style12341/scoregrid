package com.scoregrid.auth.auth.domain.port.out;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(Long id);

    /** One lookup for the login form, which accepts either identifier. */
    Optional<User> findByUsernameOrEmail(String usernameOrEmail);

    List<User> findAllByIds(List<Long> ids);

    PageResult<User> findAll(int page, int size);

    boolean existsByUsernameOrEmail(String username, String email);
}
