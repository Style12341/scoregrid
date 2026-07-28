package com.scoregrid.score.score.infrastructure.web;

import com.scoregrid.score.score.domain.model.GlobalRankingEntry;
import com.scoregrid.score.score.domain.model.TournamentRankingEntry;
import com.scoregrid.score.score.domain.port.in.GetRankingsUseCase;
import com.scoregrid.score.score.domain.port.in.RecalculateUseCase;
import com.scoregrid.score.shared.config.SecurityConfig;
import com.scoregrid.score.shared.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingController.class)
@Import({SecurityConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "scoregrid.jwt.secret=test-only-secret-at-least-32-bytes-long-for-hs256",
        "scoregrid.jwt.issuer=scoregrid-auth"
})
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetRankingsUseCase getRankings;

    @MockitoBean
    private RecalculateUseCase recalculate;

    @Test
    @DisplayName("GET /api/rankings/tournament/{id} returns 200 with paginated entries")
    void tournamentRankingReturns200() throws Exception {
        var entry = new TournamentRankingEntry(1, "42", "maxi", 10, 5, 2, 6, 0.83);
        given(getRankings.getTournamentRanking("t1", 0, 50)).willReturn(List.of(entry));

        mockMvc.perform(get("/api/rankings/tournament/t1")
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].userId").value("42"))
                .andExpect(jsonPath("$[0].username").value("maxi"))
                .andExpect(jsonPath("$[0].points").value(10))
                .andExpect(jsonPath("$[0].exactHits").value(2))
                .andExpect(jsonPath("$[0].hits").value(5));
    }

    @Test
    @DisplayName("GET /api/rankings/global returns 200 with entries")
    void globalRankingReturns200() throws Exception {
        var entry = new GlobalRankingEntry(1, "42", "maxi", 30, 2, 15, 6, 18, 0.83, 15.0);
        given(getRankings.getGlobalRanking(0, 50)).willReturn(List.of(entry));

        mockMvc.perform(get("/api/rankings/global")
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].userId").value("42"))
                .andExpect(jsonPath("$[0].totalPoints").value(30))
                .andExpect(jsonPath("$[0].tournamentsPlayed").value(2))
                .andExpect(jsonPath("$[0].averagePointsPerTournament").value(15.0));
    }

    @Test
    @DisplayName("GET /api/rankings/user/{userId} returns 200 with entries")
    void userRankingReturns200() throws Exception {
        var entry = new TournamentRankingEntry(1, "42", "maxi", 10, 5, 2, 6, 0.83);
        given(getRankings.getUserRanking("42")).willReturn(List.of(entry));

        mockMvc.perform(get("/api/rankings/user/42")
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    @DisplayName("GET /api/rankings/global without JWT returns 401")
    void globalRankingUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/rankings/global"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/rankings/recalculate/match/{id} requires ADMIN")
    void recalculateMatchRejectsPlayer() throws Exception {
        mockMvc.perform(post("/api/rankings/recalculate/match/m1")
                        .with(jwt().jwt(j -> {
                            j.subject("42");
                            j.claim("roles", List.of("PLAYER"));
                        }))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/rankings/recalculate/match/{id} accepts ADMIN")
    void recalculateMatchAcceptsAdmin() throws Exception {
        mockMvc.perform(post("/api/rankings/recalculate/match/m1")
                        .with(jwt().jwt(j -> {
                            j.subject("42");
                            j.claim("roles", List.of("ADMIN"));
                        }))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/rankings/recalculate/tournament/{id} requires ADMIN")
    void recalculateTournamentRejectsPlayer() throws Exception {
        mockMvc.perform(post("/api/rankings/recalculate/tournament/t1")
                        .with(jwt().jwt(j -> {
                            j.subject("42");
                            j.claim("roles", List.of("PLAYER"));
                        }))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/rankings/tournament/{id} with pagination params")
    void tournamentRankingRespectsPagination() throws Exception {
        given(getRankings.getTournamentRanking("t1", 1, 10)).willReturn(List.of());

        mockMvc.perform(get("/api/rankings/tournament/t1")
                        .param("page", "1")
                        .param("size", "10")
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isOk());
    }
}
