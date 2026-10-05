package com.scoregrid.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP against a RANDOM_PORT gateway: MockMvc does not perform servlet forwards. */
final class GatewayTestSupport {

    static final String SECRET = "test-only-gateway-secret-at-least-32-bytes-long";
    static final String ISSUER = "scoregrid-auth";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GatewayTestSupport() {
    }

    /** User token shaped like auth-service's JwtTokenIssuer output. */
    static String userToken() {
        return token(JwtClaimsSet.builder().issuer(ISSUER).subject("42").claim("roles", List.of("PLAYER")));
    }

    /** Internal token shaped like ServiceTokenProvider output: no iss, ADMIN role. */
    static String serviceToken() {
        return token(JwtClaimsSet.builder().subject("score-service").claim("roles", List.of("ADMIN")));
    }

    private static String token(JwtClaimsSet.Builder claims) {
        Instant now = Instant.now();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.issuedAt(now).expiresAt(now.plusSeconds(3600)).build()))
                .getTokenValue();
    }

    static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpRequest.Builder get(int port, String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    static HttpRequest.Builder postJson(int port, String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    /** The contract error envelope (docs/contracts.md#error-envelope), whoever wrote it. */
    static void assertEnvelope(HttpResponse<String> response, int status, String errorCode, String path) {
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        JsonNode body = json(response);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asString()).isEqualTo(errorCode);
        assertThat(body.get("message").asString()).isNotBlank();
        assertThat(body.get("path").asString()).isEqualTo(path);
        // ISO-8601 string, not epoch numbers.
        assertThat(body.get("timestamp").isString()).isTrue();
    }

    /** A port nothing listens on: connecting to it is refused straight away. */
    static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // One thread per exchange: a deliberately slow handler must not
            // stall the other stubbed endpoints.
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
