package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.domain.model.PageResult;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.GetUsersUseCase;
import com.scoregrid.auth.shared.config.SecurityConfig;
import com.scoregrid.auth.shared.error.DomainException;
import com.scoregrid.auth.shared.error.ErrorKind;
import com.scoregrid.auth.shared.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "scoregrid.jwt.secret=test-only-secret-at-least-32-bytes-long-for-hs256",
        "scoregrid.jwt.issuer=scoregrid-auth",
        "scoregrid.jwt.ttl=PT24H"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetUsersUseCase getUsers;

    private static User maxi() {
        return new User(42L, "maxi", "maxi@example.com", "hashed", Set.of(Role.PLAYER));
    }

    private static User ana() {
        return new User(7L, "ana", "ana@example.com", "hashed", Set.of(Role.PLAYER));
    }

    @Test
    @DisplayName("a public profile exposes id and username, never the email")
    void publicProfileHidesTheEmail() throws Exception {
        given(getUsers.byId("42")).willReturn(maxi());

        mockMvc.perform(get("/api/users/42").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.username").value("maxi"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test
    void missingUserReturnsTheNotFoundEnvelope() throws Exception {
        willThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND", "No user with id 999."))
                .given(getUsers).byId("999");

        mockMvc.perform(get("/api/users/999").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/users/999"));
    }

    @Test
    @DisplayName("batch returns id and username pairs for Score Service")
    void batchReturnsSummaries() throws Exception {
        given(getUsers.byIds(List.of("42", "7"))).willReturn(List.of(maxi(), ana()));

        mockMvc.perform(get("/api/users/batch").param("ids", "42,7").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("42"))
                .andExpect(jsonPath("$[0].username").value("maxi"))
                .andExpect(jsonPath("$[1].username").value("ana"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("\"batch\" is routed as a literal, not captured as an id")
    void batchIsNotMistakenForAnId() throws Exception {
        given(getUsers.byIds(anyList())).willReturn(List.of(maxi()));

        mockMvc.perform(get("/api/users/batch").param("ids", "42").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void batchTrimsWhitespaceAndIgnoresEmptyEntries() throws Exception {
        given(getUsers.byIds(List.of("42", "7"))).willReturn(List.of(maxi(), ana()));

        mockMvc.perform(get("/api/users/batch").param("ids", " 42 , ,7,").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void batchOverTheLimitIsRejected() throws Exception {
        willThrow(new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                "At most 200 ids per call; got 201."))
                .given(getUsers).byIds(anyList());

        mockMvc.perform(get("/api/users/batch").param("ids", "1,2,3").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("listing users is ADMIN only")
    void listRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/users").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLAYER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        given(getUsers.list(anyInt(), anyInt()))
                .willReturn(new PageResult<>(List.of(maxi(), ana()), 0, 20, 2));

        mockMvc.perform(get("/api/users").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("maxi"))
                // The admin listing is the one place the email is legitimately shown.
                .andExpect(jsonPath("$.items[0].email").value("maxi@example.com"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listRejectsANonsensePageSize() throws Exception {
        mockMvc.perform(get("/api/users").param("size", "0").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void everyUserEndpointRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/users/42")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/batch").param("ids", "42")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
