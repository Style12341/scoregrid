package com.scoregrid.auth.auth.application;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-rolled stand-in for the real adapter. A mock would let these tests pass
 * while asserting nothing about how the repository is actually used.
 */
class InMemoryUserRepository implements UserRepositoryPort {

    private final Map<Long, User> stored = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public User save(User user) {
        Long id = user.id() != null ? user.id() : sequence.incrementAndGet();
        User persisted = user.withId(id);
        stored.put(id, persisted);
        return persisted;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        return stored.values().stream()
                .filter(u -> u.username().equals(usernameOrEmail) || u.email().equals(usernameOrEmail))
                .findFirst();
    }

    @Override
    public List<User> findAllByIds(List<Long> ids) {
        List<User> found = new ArrayList<>();
        for (Long id : ids) {
            User user = stored.get(id);
            if (user != null) {
                found.add(user);
            }
        }
        return found;
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        List<User> all = new ArrayList<>(stored.values());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.subList(from, to), page, size, all.size());
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        return stored.values().stream()
                .anyMatch(u -> u.username().equals(username) || u.email().equals(email));
    }
}
