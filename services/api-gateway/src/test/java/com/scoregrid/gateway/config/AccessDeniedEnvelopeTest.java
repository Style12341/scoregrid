package com.scoregrid.gateway.config;

import com.scoregrid.gateway.error.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every edge rule today is "authenticated", so no request can reach the 403
 * path end to end. The handler is still wired; this pins its output.
 */
class AccessDeniedEnvelopeTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void accessDenied_isWrittenAsTheContractEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/tournaments/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.accessDeniedHandler(new ErrorResponseWriter(jsonMapper))
                .handle(request, response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asString()).isEqualTo("FORBIDDEN");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("path").asString()).isEqualTo("/api/tournaments/1");
    }
}
