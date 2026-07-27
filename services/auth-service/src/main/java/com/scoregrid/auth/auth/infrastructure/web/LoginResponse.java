package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.Authentication;

import java.time.Instant;

record LoginResponse(String token, Instant expiresAt, UserProfileResponse user) {

    static LoginResponse from(Authentication authentication) {
        return new LoginResponse(
                authentication.token().value(),
                authentication.token().expiresAt(),
                UserProfileResponse.from(authentication.user()));
    }
}
