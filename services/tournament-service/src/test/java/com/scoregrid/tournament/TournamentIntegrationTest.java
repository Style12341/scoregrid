package com.scoregrid.tournament;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test exercising the full Spring Security filter chain
 * through a real application context backed by Testcontainers.
 *
 * <p>Uses {@code .with(jwt())} to set up a mock JWT in the security context,
 * which is required because {@link com.scoregrid.tournament.shared.security.CurrentUser}
 * reads the user ID from the JWT {@code sub} claim directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TournamentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Creates a JWT post-processor with the given subject and roles. */
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWith(String subject, String... roles) {
        var authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return jwt().jwt(j -> j.subject(subject)).authorities(authorities);
    }

    // -- Full flow: create → activate → join → verify ---------------------------

    @Test
    void shouldCreateActivateAndJoinTournament() throws Exception {
        // 1. Create a DRAFT tournament as ADMIN
        var location = mockMvc.perform(post("/api/tournaments")
                        .with(jwtWith("42", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Copa Integracion","description":"Torneo de prueba","startDate":"2027-06-01","endDate":"2027-07-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.name").value("Copa Integracion"))
                .andReturn().getResponse().getHeader("Location");

        assert location != null;
        var tournamentId = location.substring(location.lastIndexOf('/') + 1);

        // 2. Activate the tournament (DRAFT → ACTIVE)
        mockMvc.perform(patch("/api/tournaments/" + tournamentId + "/status")
                        .with(jwtWith("42", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 3. Join as PLAYER
        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/join")
                        .with(jwtWith("42", "PLAYER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournamentId").value(tournamentId))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.joinedAt").isNotEmpty());

        // 4. Verify enrolment via participants list
        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/participants")
                        .with(jwtWith("42", "PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tournamentId").value(tournamentId))
                .andExpect(jsonPath("$[0].userId").value("42"));
    }

    // -- Role enforcement (full security chain) ---------------------------------

    @Test
    void shouldRejectPlayerCreatingTournament() throws Exception {
        mockMvc.perform(post("/api/tournaments")
                        .with(jwtWith("42", "PLAYER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Unauthorized Tournament"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPlayerCreatingTeam() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .with(jwtWith("42", "PLAYER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Argentina"}"""))
                .andExpect(status().isForbidden());
    }

    // -- Team creation and listing ----------------------------------------------

    @Test
    void shouldCreateAndListTeam() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .with(jwtWith("42", "ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Argentina","shortName":"ARG","country":"AR"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Argentina"));

        mockMvc.perform(get("/api/teams")
                        .with(jwtWith("99", "PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Argentina"));
    }

    // -- Tournament-team assignment with string IDs -----------------------------

    @Test
    void shouldAssignTeamsToTournament() throws Exception {
        // Create two teams
        var team1Response = mockMvc.perform(post("/api/teams")
                        .with(jwtWith("42", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Argentina","shortName":"ARG"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var team1Id = com.jayway.jsonpath.JsonPath.read(team1Response, "$.id");

        var team2Response = mockMvc.perform(post("/api/teams")
                        .with(jwtWith("42", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Brazil","shortName":"BRA"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var team2Id = com.jayway.jsonpath.JsonPath.read(team2Response, "$.id");

        // Create a tournament
        var location = mockMvc.perform(post("/api/tournaments")
                        .with(jwtWith("42", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Copa America","startDate":"2027-06-01","endDate":"2027-07-01"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assert location != null;
        var tournamentId = location.substring(location.lastIndexOf('/') + 1);

        // Assign teams to tournament using string IDs (per contracts.md §Teams)
        mockMvc.perform(post("/api/tournaments/" + tournamentId + "/teams")
                        .with(jwtWith("42", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamIds\":[\"" + team1Id + "\",\"" + team2Id + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // List tournament teams
        mockMvc.perform(get("/api/tournaments/" + tournamentId + "/teams")
                        .with(jwtWith("99", "PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
