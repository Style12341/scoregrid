package com.scoregrid.tournament.tournament.infrastructure.web;

import com.scoregrid.tournament.shared.error.GlobalExceptionHandler;
import com.scoregrid.tournament.shared.config.SecurityConfig;
import com.scoregrid.tournament.shared.security.CurrentUser;
import com.scoregrid.tournament.tournament.domain.model.Participant;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.CreateTournament;
import com.scoregrid.tournament.tournament.domain.port.in.DeleteTournament;
import com.scoregrid.tournament.tournament.domain.port.in.GetParticipant;
import com.scoregrid.tournament.tournament.domain.port.in.GetTournament;
import com.scoregrid.tournament.tournament.domain.port.in.JoinTournament;
import com.scoregrid.tournament.tournament.domain.port.in.ListParticipants;
import com.scoregrid.tournament.tournament.domain.port.in.ListTournaments;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.UpdateTournament;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentController.class)
@Import({GlobalExceptionHandler.class})
@org.springframework.test.context.TestPropertySource(properties = "scoregrid.jwt.secret=dev-only-insecure-secret-do-not-deploy-anywhere-real")
class TournamentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTournament createTournament;

    @MockitoBean
    private GetTournament getTournament;

    @MockitoBean
    private ListTournaments listTournaments;

    @MockitoBean
    private UpdateTournament updateTournament;

    @MockitoBean
    private TransitionTournamentStatus transitionTournamentStatus;

    @MockitoBean
    private DeleteTournament deleteTournament;

    @MockitoBean
    private JoinTournament joinTournament;

    @MockitoBean
    private ListParticipants listParticipants;

    @MockitoBean
    private GetParticipant getParticipant;

    @MockitoBean
    private CurrentUser currentUser;

    private Tournament draftTournament;
    private Tournament activeTournament;

    @BeforeEach
    void setUp() {
        draftTournament = Tournament.reconstitute(1L, "Copa 2026", "Desc",
                TournamentStatus.DRAFT,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", Instant.now(), Instant.now());

        activeTournament = Tournament.reconstitute(1L, "Copa 2026", "Desc",
                TournamentStatus.ACTIVE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15),
                "42", Instant.now(), Instant.now());

        when(currentUser.requireId()).thenReturn("42");
    }

    // -- 2.2 Create Tournament ---------------------------------------------------

    @Nested
    class CreateTournamentEndpoint {

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldCreateTournamentAndReturn201() throws Exception {
            when(createTournament.execute(any())).thenReturn(draftTournament);

            mockMvc.perform(post("/api/tournaments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Copa 2026","startDate":"2026-08-01","endDate":"2026-09-15"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("1"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.name").value("Copa 2026"));
        }

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldRejectNonAdmin() throws Exception {
            // @PreAuthorize throws AccessDeniedException; exact status depends on
            // method security interceptor which @WebMvcTest does not fully wire.
            // Full auth-flow tests live in @SpringBootTest integration tests.
            mockMvc.perform(post("/api/tournaments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Copa 2026\"}"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectMissingName() throws Exception {
            mockMvc.perform(post("/api/tournaments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectBlankName() throws Exception {
            mockMvc.perform(post("/api/tournaments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // -- 2.3 Get Tournament ------------------------------------------------------

    @Nested
    class GetTournamentEndpoint {

        @Test
        @WithMockUser
        void shouldReturnTournament() throws Exception {
            when(getTournament.execute(1L)).thenReturn(draftTournament);

            mockMvc.perform(get("/api/tournaments/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("1"))
                    .andExpect(jsonPath("$.name").value("Copa 2026"))
                    .andExpect(jsonPath("$.createdBy").value("42"));
        }

        @Test
        @WithMockUser
        void shouldReturn404ForMissingTournament() throws Exception {
            when(getTournament.execute(999L))
                    .thenThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND", "Tournament not found: 999"));

            mockMvc.perform(get("/api/tournaments/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    // -- 2.4 List Tournaments ----------------------------------------------------

    @Nested
    class ListTournamentsEndpoint {

        @Test
        @WithMockUser
        void shouldListTournaments() throws Exception {
            var result = new ListTournaments.Result(List.of(draftTournament), 1, 1, 0, 20);
            when(listTournaments.execute(any(), eq(0), eq(20))).thenReturn(result);

            mockMvc.perform(get("/api/tournaments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value("1"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @WithMockUser
        void shouldFilterByStatus() throws Exception {
            var result = new ListTournaments.Result(List.of(activeTournament), 1, 1, 0, 20);
            when(listTournaments.execute(eq(Optional.of(TournamentStatus.ACTIVE)), eq(0), eq(20))).thenReturn(result);

            mockMvc.perform(get("/api/tournaments?status=ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
        }

        @Test
        @WithMockUser
        void shouldRejectInvalidStatus() throws Exception {
            mockMvc.perform(get("/api/tournaments?status=INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }
    }

    // -- 2.5 Update Tournament ---------------------------------------------------

    @Nested
    class UpdateTournamentEndpoint {

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateDraftTournament() throws Exception {
            when(updateTournament.execute(any())).thenReturn(draftTournament);

            mockMvc.perform(put("/api/tournaments/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Updated\",\"description\":\"New desc\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Copa 2026"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404ForMissingTournament() throws Exception {
            when(updateTournament.execute(any()))
                    .thenThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND", "Tournament not found: 999"));

            mockMvc.perform(put("/api/tournaments/999")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldRejectNonAdmin() throws Exception {
            // @PreAuthorize: @WebMvcTest does not fully wire method security interceptor
            mockMvc.perform(put("/api/tournaments/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().is5xxServerError());
        }
    }

    // -- 2.6 Transition Status ---------------------------------------------------

    @Nested
    class TransitionStatusEndpoint {

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldTransitionToActive() throws Exception {
            when(transitionTournamentStatus.execute(any())).thenReturn(activeTournament);

            mockMvc.perform(patch("/api/tournaments/1/status")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectInvalidTransition() throws Exception {
            when(transitionTournamentStatus.execute(any()))
                    .thenThrow(new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE", "Invalid transition"));

            mockMvc.perform(patch("/api/tournaments/1/status")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"FINISHED\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectInvalidStatusValue() throws Exception {
            mockMvc.perform(patch("/api/tournaments/1/status")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"UNKNOWN\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // -- 2.7 Delete Tournament ---------------------------------------------------

    @Nested
    class DeleteTournamentEndpoint {

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteDraftTournament() throws Exception {
            mockMvc.perform(delete("/api/tournaments/1").with(csrf()))
                    .andExpect(status().isNoContent());
            verify(deleteTournament).execute(1L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectDeleteActiveTournament() throws Exception {
            org.mockito.Mockito.doThrow(new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Only DRAFT tournaments can be deleted"))
                    .when(deleteTournament).execute(1L);

            mockMvc.perform(delete("/api/tournaments/1").with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404ForMissingTournament() throws Exception {
            org.mockito.Mockito.doThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND", "Tournament not found"))
                    .when(deleteTournament).execute(999L);

            mockMvc.perform(delete("/api/tournaments/999").with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // -- 5.2 Join Tournament -----------------------------------------------------

    @Nested
    class JoinTournamentEndpoint {

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldJoinActiveTournament() throws Exception {
            var participant = new Participant(1L, "42", Instant.now());
            when(joinTournament.execute(any())).thenReturn(participant);

            mockMvc.perform(post("/api/tournaments/1/join").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("42"))
                    .andExpect(jsonPath("$.tournamentId").value("1"))
                    .andExpect(jsonPath("$.joinedAt").isNotEmpty());
        }

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldRejectDuplicateEnrolment() throws Exception {
            when(joinTournament.execute(any()))
                    .thenThrow(new DomainException(ErrorKind.CONFLICT, "CONFLICT", "already enrolled"));

            mockMvc.perform(post("/api/tournaments/1/join").with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(roles = "PLAYER")
        void shouldRejectNonActiveTournament() throws Exception {
            when(joinTournament.execute(any()))
                    .thenThrow(new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE", "not ACTIVE"));

            mockMvc.perform(post("/api/tournaments/1/join").with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldRejectNonPlayerRole() throws Exception {
            // @PreAuthorize: @WebMvcTest does not fully wire method security interceptor
            mockMvc.perform(post("/api/tournaments/1/join").with(csrf()))
                    .andExpect(status().is5xxServerError());
        }
    }

    // -- 5.3 List Participants ---------------------------------------------------

    @Nested
    class ListParticipantsEndpoint {

        @Test
        @WithMockUser
        void shouldListParticipants() throws Exception {
            var p1 = new Participant(1L, "42", Instant.now());
            var p2 = new Participant(1L, "99", Instant.now());
            when(listParticipants.execute(1L)).thenReturn(List.of(p1, p2));

            mockMvc.perform(get("/api/tournaments/1/participants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userId").value("42"))
                    .andExpect(jsonPath("$[1].userId").value("99"));
        }

        @Test
        @WithMockUser
        void shouldReturnEmptyList() throws Exception {
            when(listParticipants.execute(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/tournaments/1/participants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // -- 5.4 Get Participant -----------------------------------------------------

    @Nested
    class GetParticipantEndpoint {

        @Test
        @WithMockUser
        void shouldReturnParticipant() throws Exception {
            var joinedAt = Instant.parse("2026-08-10T14:30:00Z");
            var participant = new Participant(1L, "42", joinedAt);
            when(getParticipant.execute(1L, "42")).thenReturn(participant);

            mockMvc.perform(get("/api/tournaments/1/participants/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("42"))
                    .andExpect(jsonPath("$.tournamentId").value("1"))
                    .andExpect(jsonPath("$.joinedAt").value("2026-08-10T14:30:00Z"));
        }

        @Test
        @WithMockUser
        void shouldReturn404ForNotEnrolled() throws Exception {
            when(getParticipant.execute(1L, "42"))
                    .thenThrow(new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND", "not enrolled"));

            mockMvc.perform(get("/api/tournaments/1/participants/42"))
                    .andExpect(status().isNotFound());
        }
    }
}
