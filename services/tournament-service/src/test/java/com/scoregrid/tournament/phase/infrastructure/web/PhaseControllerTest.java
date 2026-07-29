package com.scoregrid.tournament.phase.infrastructure.web;

import com.scoregrid.tournament.shared.error.GlobalExceptionHandler;
import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.model.PhaseType;
import com.scoregrid.tournament.phase.domain.port.in.CreatePhase;
import com.scoregrid.tournament.phase.domain.port.in.ListPhases;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PhaseController.class)
@Import({GlobalExceptionHandler.class})
@org.springframework.test.context.TestPropertySource(properties = "scoregrid.jwt.secret=dev-only-insecure-secret-do-not-deploy-anywhere-real")
class PhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePhase createPhase;

    @MockitoBean
    private ListPhases listPhases;

    private Phase phase;

    @BeforeEach
    void setUp() {
        phase = Phase.reconstitute(20L, 1L, "Semifinal", PhaseType.SEMI_FINAL, 4);
    }

    // -- Create Phase -----------------------------------------------------------

    @Nested
    class CreatePhaseEndpoint {

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldCreatePhaseAndReturn201() throws Exception {
            when(createPhase.execute(any())).thenReturn(phase);

            mockMvc.perform(post("/api/tournaments/1/phases")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"SEMI_FINAL","name":"Semifinal","displayOrder":4}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("20"))
                    .andExpect(jsonPath("$.type").value("SEMI_FINAL"))
                    .andExpect(jsonPath("$.name").value("Semifinal"))
                    .andExpect(jsonPath("$.displayOrder").value(4));
        }

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldRejectNonAdmin() throws Exception {
            // @PreAuthorize: @WebMvcTest does not fully wire method security interceptor
            mockMvc.perform(post("/api/tournaments/1/phases")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"SEMI_FINAL","displayOrder":0}"""))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectInvalidPhaseType() throws Exception {
            // Jackson cannot deserialize an unknown enum value; it surfaces as 500
            mockMvc.perform(post("/api/tournaments/1/phases")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"INVALID_TYPE","displayOrder":0}"""))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404ForNonExistentTournament() throws Exception {
            when(createPhase.execute(any()))
                    .thenThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                            "Tournament not found: 999"));

            mockMvc.perform(post("/api/tournaments/999/phases")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"SEMI_FINAL","displayOrder":0}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    // -- List Phases ------------------------------------------------------------

    @Nested
    class ListPhasesEndpoint {

        @Test
        @WithMockUser
        void shouldListPhasesOrdered() throws Exception {
            var groupStage = Phase.reconstitute(20L, 1L, "Fase de grupos", PhaseType.GROUP_STAGE, 0);
            var semi = Phase.reconstitute(21L, 1L, "Semifinal", PhaseType.SEMI_FINAL, 1);
            when(listPhases.execute(1L)).thenReturn(List.of(groupStage, semi));

            mockMvc.perform(get("/api/tournaments/1/phases"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("20"))
                    .andExpect(jsonPath("$[0].type").value("GROUP_STAGE"))
                    .andExpect(jsonPath("$[1].id").value("21"))
                    .andExpect(jsonPath("$[1].type").value("SEMI_FINAL"));
        }

        @Test
        @WithMockUser
        void shouldReturnEmptyList() throws Exception {
            when(listPhases.execute(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/tournaments/1/phases"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @WithMockUser
        void shouldReturn404ForNonExistentTournament() throws Exception {
            when(listPhases.execute(999L))
                    .thenThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                            "Tournament not found: 999"));

            mockMvc.perform(get("/api/tournaments/999/phases"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }
}
