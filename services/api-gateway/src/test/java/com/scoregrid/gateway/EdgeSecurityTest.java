package com.scoregrid.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.scoregrid.gateway.GatewayTestSupport.assertEnvelope;
import static com.scoregrid.gateway.GatewayTestSupport.get;
import static com.scoregrid.gateway.GatewayTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's edge checks: who gets past it, and what a rejection looks like.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "scoregrid.jwt.secret=" + GatewayTestSupport.SECRET,
        "scoregrid.jwt.issuer=" + GatewayTestSupport.ISSUER,
        "scoregrid.cors.allowed-origins=http://localhost:3000,http://localhost:5173"
})
class EdgeSecurityTest {

    private static final HttpServer TOURNAMENT_STUB = GatewayTestSupport.startStub();

    static {
        TOURNAMENT_STUB.createContext("/api/tournaments",
                exchange -> GatewayTestSupport.respond(exchange, 200, "[]"));
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("SCOREGRID_ROUTES_TOURNAMENT_URI",
                () -> "http://127.0.0.1:" + TOURNAMENT_STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        TOURNAMENT_STUB.stop(0);
    }

    @LocalServerPort
    int port;

    @Test
    void requestWithoutToken_isRejectedWithTheContractEnvelope() throws Exception {
        HttpResponse<String> response = send(get(port, "/api/tournaments", null));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                value -> assertThat(value).startsWith("Bearer"));
        assertEnvelope(response, 401, "UNAUTHORIZED", "/api/tournaments");
    }

    @Test
    void internalServiceToken_withoutIssuer_isRejectedAtTheEdge() throws Exception {
        HttpResponse<String> response = send(get(port, "/api/tournaments", GatewayTestSupport.serviceToken()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertEnvelope(response, 401, "UNAUTHORIZED", "/api/tournaments");
    }

    @Test
    void userToken_withIssuer_isRoutedDownstream() throws Exception {
        // Positive control for the two rejections above: same secret, same
        // route, the only difference is a valid iss.
        HttpResponse<String> response = send(get(port, "/api/tournaments", GatewayTestSupport.userToken()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void corsPreflight_fromTheFrontendOrigin_isAllowed() throws Exception {
        HttpResponse<String> response = send(preflight("http://localhost:3000"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:3000");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods")).hasValueSatisfying(
                methods -> assertThat(methods).contains("GET"));
    }

    @Test
    void corsPreflight_fromAnUnknownOrigin_isRefused() throws Exception {
        HttpResponse<String> response = send(preflight("http://evil.example"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpRequest.Builder preflight(String origin) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/tournaments"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization");
    }
}
