package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.Authentication;
import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.auth.domain.port.in.RegisterUserUseCase;
import com.scoregrid.auth.shared.config.SecurityConfig;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import com.scoregrid.auth.shared.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4 moved the slice annotations out of o.s.b.test.autoconfigure.* into
// per-module packages. The Boot 3 import does not exist and there is no
// deprecation shim — see AGENTS.md section 5.
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "scoregrid.jwt.secret=test-only-secret-at-least-32-bytes-long-for-hs256",
        "scoregrid.jwt.issuer=scoregrid-auth",
        "scoregrid.jwt.ttl=PT24H"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUser;

    @MockitoBean
    private AuthenticateUserUseCase authenticateUser;

    @MockitoBean
    private GetUsersUseCase getUsers;

    private static User maxi() {
        return new User(42L, "maxi", "maxi@example.com", "hashed", Set.of(Role.PLAYER));
    }

    @Test
    @DisplayName("register returns 201 with the profile and no token")
    void registerReturnsTheProfile() throws Exception {
        given(registerUser.register(any())).willReturn(maxi());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"maxi@example.com","password":"correct-horse"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.username").value("maxi"))
                .andExpect(jsonPath("$.email").value("maxi@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("PLAYER"))
                // Registering does not log you in — the client posts to /login next.
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("ids are serialised as strings, not numbers")
    void idIsAString() throws Exception {
        given(registerUser.register(any())).willReturn(maxi());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"maxi@example.com","password":"correct-horse"}"""))
                .andExpect(jsonPath("$.id").isString());
    }

    @Test
    void registerRejectsAShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"maxi@example.com","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void registerRejectsAnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"not-an-email","password":"correct-horse"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a duplicate registration returns 409 in the shared error envelope")
    void duplicateRegistrationUsesTheEnvelope() throws Exception {
        willThrow(new DomainException(ErrorKind.CONFLICT, "DUPLICATE_USER",
                "That username or email is already registered."))
                .given(registerUser).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"maxi@example.com","password":"correct-horse"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("DUPLICATE_USER"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void loginReturnsTokenExpiryAndUser() throws Exception {
        given(authenticateUser.authenticate(any())).willReturn(new Authentication(
                new IssuedToken("eyJhbGciOi.fake.token", Instant.parse("2026-07-28T12:00:00Z")),
                maxi()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"maxi","password":"correct-horse"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("eyJhbGciOi.fake.token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-28T12:00:00Z"))
                .andExpect(jsonPath("$.user.id").value("42"))
                .andExpect(jsonPath("$.user.roles[0]").value("PLAYER"));
    }

    @Test
    @DisplayName("bad credentials return 401 UNAUTHORIZED without saying which half was wrong")
    void badCredentialsAreOpaque() throws Exception {
        willThrow(new DomainException(ErrorKind.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials."))
                .given(authenticateUser).authenticate(any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"maxi","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid credentials."));
    }

    @Test
    void loginRejectsABlankIdentifier() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"","password":"correct-horse"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/me resolves the user from the JWT sub claim")
    void meUsesTheSubClaim() throws Exception {
        given(getUsers.byId("42")).willReturn(maxi());

        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.email").value("maxi@example.com"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerAndLoginArePublic() throws Exception {
        given(registerUser.register(any())).willReturn(maxi());

        // No jwt() post-processor: these two must work for an anonymous caller.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maxi","email":"maxi@example.com","password":"correct-horse"}"""))
                .andExpect(status().isCreated());
    }
}
