package com.scoregrid.gateway.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the contract error envelope straight onto the servlet response.
 *
 * <p>The gateway's failures happen outside any controller — in the security
 * filter chain, in a proxied call, in a circuit breaker fallback — so there is
 * no {@code @RestControllerAdvice} to lean on. Every one of those paths ends
 * here, which is what keeps "the same shape on every error" true at the edge.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), errorCode, message, originalPath(request)));
    }

    /**
     * The path the client asked for. A circuit breaker fallback is a servlet
     * forward, so {@code getRequestURI()} would report the fallback path.
     */
    static String originalPath(HttpServletRequest request) {
        Object forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        return forwarded != null ? forwarded.toString() : request.getRequestURI();
    }
}
