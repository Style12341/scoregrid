package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.LoginCommand;
import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.auth.domain.port.in.RegisterUserUseCase;
import com.scoregrid.auth.auth.domain.port.in.RegisterUserUseCase.RegisterCommand;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import com.scoregrid.auth.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** docs/contracts.md#auth-service — /api/auth. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;
    private final GetUsersUseCase getUsers;
    private final CurrentUser currentUser;

    AuthController(RegisterUserUseCase registerUser,
                   AuthenticateUserUseCase authenticateUser,
                   GetUsersUseCase getUsers,
                   CurrentUser currentUser) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.getUsers = getUsers;
        this.currentUser = currentUser;
    }

    /**
     * Returns the profile, not a token: registering does not log you in.
     * The client posts to {@code /login} next — contract, 201 Created.
     */
    @PostMapping("/register")
    ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        User created = registerUser.register(
                new RegisterCommand(request.username(), request.email(), request.password()));

        return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(created));
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authenticateUser.authenticate(
                new LoginCommand(request.usernameOrEmail(), request.password())));
    }

    /** The acting user comes from the JWT 'sub' claim. Never from the request. */
    @GetMapping("/me")
    UserProfileResponse me() {
        String userId = currentUser.id().orElseThrow(() -> new DomainException(
                ErrorKind.UNAUTHORIZED, "UNAUTHORIZED", "No authenticated user."));

        return UserProfileResponse.from(getUsers.byId(userId));
    }
}
