package com.scoregrid.score.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The acting user, always taken from the JWT 'sub' claim.
 *
 * <p>NEVER read the user id from a request body or query parameter. Accepting a
 * caller-supplied userId is a privilege escalation bug, not a convenience.
 */
@Component
public class CurrentUser {

    public Optional<String> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.ofNullable(jwt.getSubject());
    }

    public String requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
