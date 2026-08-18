package com.scoregrid.prediction.prediction.infrastructure.web;

import com.scoregrid.prediction.prediction.domain.model.DerivedOutcome;
import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.model.PredictionType;
import com.scoregrid.prediction.prediction.domain.port.in.CreatePredictionUseCase;
import com.scoregrid.prediction.prediction.domain.port.in.GetPredictionsUseCase;
import com.scoregrid.prediction.prediction.domain.port.in.UpdatePredictionUseCase;
import com.scoregrid.prediction.shared.config.SecurityConfig;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import com.scoregrid.prediction.shared.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionController.class)
@Import({SecurityConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "scoregrid.jwt.secret=test-only-secret-at-least-32-bytes-long-for-hs256",
        "scoregrid.jwt.issuer=scoregrid-auth"
})
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePredictionUseCase createPrediction;

    @MockitoBean
    private UpdatePredictionUseCase updatePrediction;

    @MockitoBean
    private GetPredictionsUseCase getPredictions;

    private static RequestPostProcessor jwtWithRole(String role) {
        return jwt()
                .jwt(j -> j.subject("42"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static Prediction samplePrediction() {
        return new Prediction("p1", "42", "t1", "m1",
                PredictionType.EXACT_SCORE, 2, 1, DerivedOutcome.HOME_WIN,
                false, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("POST /api/predictions creates and returns 201")
    void createReturnsCreated() throws Exception {
        given(createPrediction.create(eq("42"), eq("m1"), eq(2), eq(1)))
                .willReturn(samplePrediction());

        mockMvc.perform(post("/api/predictions")
                        .with(jwtWithRole("PLAYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchId":"m1","homeScore":2,"awayScore":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("p1"))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.matchId").value("m1"))
                .andExpect(jsonPath("$.homeScore").value(2))
                .andExpect(jsonPath("$.awayScore").value(1))
                .andExpect(jsonPath("$.derivedOutcome").value("HOME_WIN"))
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    @DisplayName("POST /api/predictions without JWT returns 401")
    void createUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchId":"m1","homeScore":2,"awayScore":1}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/predictions returns 409 on PREDICTION_LOCKED")
    void lockedPredictionReturns409() throws Exception {
        given(createPrediction.create(eq("42"), eq("m1"), eq(2), eq(1)))
                .willThrow(new DomainException(ErrorKind.CONFLICT, "PREDICTION_LOCKED",
                        "Match has already started; predictions are locked."));

        mockMvc.perform(post("/api/predictions")
                        .with(jwtWithRole("PLAYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matchId":"m1","homeScore":2,"awayScore":1}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("PREDICTION_LOCKED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/predictions/match/{matchId} requires ADMIN role")
    void matchPredictionsRequiresAdmin() throws Exception {
        given(getPredictions.getPredictionsByMatch("m1"))
                .willReturn(List.of(samplePrediction()));

        mockMvc.perform(get("/api/predictions/match/m1")
                        .with(jwtWithRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"));
    }

    @Test
    @DisplayName("GET /api/predictions/match/{matchId} rejects PLAYER role")
    void matchPredictionsRejectsPlayer() throws Exception {
        mockMvc.perform(get("/api/predictions/match/m1")
                        .with(jwtWithRole("PLAYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/predictions/me returns user's predictions")
    void getMyPredictionsReturns200() throws Exception {
        given(getPredictions.getAllMyPredictions("42", 0, 20))
                .willReturn(List.of(samplePrediction()));

        mockMvc.perform(get("/api/predictions/me")
                        .with(jwtWithRole("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"));
    }

    @Test
    @DisplayName("GET /api/predictions/me/match/{matchId} returns 404 when not found")
    void myPredictionForMatchNotFoundReturns404() throws Exception {
        given(getPredictions.getMyPredictionForMatch("42", "m99"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/predictions/me/match/m99")
                        .with(jwtWithRole("PLAYER")))
                .andExpect(status().isNotFound());
    }
}
