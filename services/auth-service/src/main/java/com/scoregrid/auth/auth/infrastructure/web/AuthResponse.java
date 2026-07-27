package com.scoregrid.auth.auth.infrastructure.web;

import java.time.Instant;
import java.util.List;

record AuthResponse(String token, Instant expiresAt, UserProfile user) {

    record UserProfile(String id, String username, String email, List<String> roles) {}
}
