package com.scoregrid.auth.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Either the username or the email goes in {@code usernameOrEmail}. */
record LoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password
) {
}
