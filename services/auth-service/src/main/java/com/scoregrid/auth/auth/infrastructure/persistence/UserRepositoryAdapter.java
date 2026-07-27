package com.scoregrid.auth.auth.infrastructure.persistence;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository users;
    private final RoleJpaRepository roles;
    private final UserMapper mapper;

    UserRepositoryAdapter(UserJpaRepository users, RoleJpaRepository roles, UserMapper mapper) {
        this.users = users;
        this.roles = roles;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = user.id() == null
                ? new UserEntity()
                : users.findById(user.id()).orElseGet(UserEntity::new);

        entity.setUsername(user.username());
        entity.setEmail(user.email());
        entity.setPassword(user.passwordHash());
        entity.setRoles(resolveRoles(user.roles()));

        try {
            return mapper.toDomain(users.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // The unique index on username/email is the duplicate rule. The
            // pre-check in RegisterUserService is a courtesy; two concurrent
            // registrations both pass it and land here.
            throw new DomainException(ErrorKind.CONFLICT, "DUPLICATE_USER",
                    "That username or email is already registered.");
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        return users.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        // One value, both columns: the login form does not care which was typed.
        return users.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .map(mapper::toDomain);
    }

    @Override
    public List<User> findAllByIds(List<Long> ids) {
        return users.findAllByIdIn(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        Page<UserEntity> found = users.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new PageResult<>(
                found.getContent().stream().map(mapper::toDomain).toList(),
                page,
                size,
                found.getTotalElements());
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        return users.existsByUsernameOrEmail(username, email);
    }

    private Set<RoleEntity> resolveRoles(Set<Role> wanted) {
        Set<RoleEntity> resolved = new LinkedHashSet<>();
        for (Role role : wanted) {
            resolved.add(roles.findByName(role.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Role " + role + " is missing from the roles table. "
                                    + "V1__create_users_roles.sql seeds it — the database is not migrated.")));
        }
        return resolved;
    }
}
