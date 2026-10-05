package com.scoregrid.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.scoregrid.gateway.GatewayTestSupport.assertEnvelope;
import static com.scoregrid.gateway.GatewayTestSupport.get;
import static com.scoregrid.gateway.GatewayTestSupport.postJson;
import static com.scoregrid.gateway.GatewayTestSupport.send;
import static com.scoregrid.gateway.GatewayTestSupport.userToken;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a client sees when the service behind a route is dead, slow or missing.
 * No Docker: downstreams are a closed local port and an in-process stub, wired
 * in through the same SCOREGRID_ROUTES_*_URI overrides the YAML exposes.
 *
 * <p>Short client timeouts keep the suite fast. The breakers' 8 s time limit
 * stays as configured, so the HTTP client timeouts are what fire here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "scoregrid.jwt.secret=" + GatewayTestSupport.SECRET,
        "scoregrid.jwt.issuer=" + GatewayTestSupport.ISSUER,
        "spring.http.clients.connect-timeout=500ms",
        "spring.http.clients.read-timeout=1s"
})
@ExtendWith(OutputCaptureExtension.class)
class DownstreamFailureTest {

    private static final int CLOSED_PORT = GatewayTestSupport.closedPort();
    private static final HttpServer STUB = GatewayTestSupport.startStub();
    private static final AtomicInteger TOURNAMENT_HITS = new AtomicInteger();
    private static final AtomicInteger BROKEN_HITS = new AtomicInteger();
    private static final AtomicInteger LOGIN_HITS = new AtomicInteger();
    private static final AtomicInteger SLOW_READ_HITS = new AtomicInteger();

    static {
        STUB.createContext("/api/tournaments", exchange -> {
            TOURNAMENT_HITS.incrementAndGet();
            // Refused attempt + 1 s back-off + this delay is past Resilience4J's
            // 1 s default time limiter: a failover only succeeds if the
            // configured limit is the one actually in force.
            sleep(300);
            GatewayTestSupport.respond(exchange, 200, "[]");
        });
        STUB.createContext("/api/auth/login", exchange -> {
            LOGIN_HITS.incrementAndGet();
            sleep(3000); // longer than the 1 s read timeout
            GatewayTestSupport.respond(exchange, 200, "{}");
        });
        STUB.createContext("/api/users/slow", exchange -> {
            SLOW_READ_HITS.incrementAndGet();
            sleep(3000); // longer than the 1 s read timeout
            GatewayTestSupport.respond(exchange, 200, "{}");
        });
        STUB.createContext("/api/auth/broken", exchange -> {
            BROKEN_HITS.incrementAndGet();
            GatewayTestSupport.respond(exchange, 500, "{\"error\":\"INTERNAL_ERROR\"}");
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String stub = "http://127.0.0.1:" + STUB.getAddress().getPort();
        String closed = "http://127.0.0.1:" + CLOSED_PORT;

        registry.add("SCOREGRID_ROUTES_AUTH_URI", () -> stub);
        registry.add("SCOREGRID_ROUTES_SCORE_URI", () -> closed);
        // Two "replicas" behind lb://tournament-service, one of them dead.
        registry.add("spring.cloud.discovery.client.simple.instances.tournament-service[0].uri", () -> closed);
        registry.add("spring.cloud.discovery.client.simple.instances.tournament-service[1].uri", () -> stub);
        // prediction-service keeps lb://prediction-service and has no instance at all.
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @LocalServerPort
    int port;

    @Test
    void getToADeadDownstream_answers503EnvelopeWithinTheTimeLimit() throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = send(get(port, "/api/rankings/global", userToken()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/rankings/global");
        // Three refused attempts, 1 s apart: about 2 s, never a hang.
        assertThat(elapsed).isLessThan(Duration.ofMillis(3500));
    }

    @Test
    void writeToADeadDownstream_answers503Envelope() throws Exception {
        HttpResponse<String> response = send(postJson(port, "/api/rankings/recalculate/match/1", userToken(), "{}"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/rankings/recalculate/match/1");
    }

    @Test
    void openBreaker_failsFastWithoutWaitingForTheDeadService() throws Exception {
        // Four refused writes reach the breaker's minimum number of calls at a
        // 100% failure rate: it opens.
        for (int i = 0; i < 4; i++) {
            send(postJson(port, "/api/rankings/recalculate/match/" + i, userToken(), "{}"));
        }

        long start = System.nanoTime();
        HttpResponse<String> response = send(get(port, "/api/rankings/global", userToken()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/rankings/global");
        // With the breaker closed this GET spends ~2 s retrying; open, it is not attempted.
        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
    }

    @Test
    void serviceWithNoRegisteredInstance_answers503Envelope() throws Exception {
        HttpResponse<String> response = send(get(port, "/api/predictions/me", userToken()));

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/predictions/me");
    }

    @Test
    void slowDownstream_isCutAtTheReadTimeout_andTheWriteIsNotReplayed() throws Exception {
        // Also a public route: the fallback forward must not be re-authorised
        // into a 401 when auth-service itself is the one down.
        int before = LOGIN_HITS.get();
        long start = System.nanoTime();
        HttpResponse<String> response = send(postJson(port, "/api/auth/login", null, "{}"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/auth/login");
        assertThat(elapsed).isLessThan(Duration.ofMillis(2000));
        // The login may have succeeded downstream; sending it again is not ours to decide.
        assertThat(LOGIN_HITS.get() - before).isEqualTo(1);
    }

    @Test
    void getsFailOverToTheLiveReplica_andEachRetryIsLogged(CapturedOutput output) throws Exception {
        int before = TOURNAMENT_HITS.get();

        // Round robin alternates between the dead (connection refused) and the
        // live instance, so without the retry about half of these would fail.
        for (int i = 0; i < 6; i++) {
            HttpResponse<String> response = send(get(port, "/api/tournaments", userToken()));
            assertThat(response.statusCode()).as("GET #%d", i + 1).isEqualTo(200);
        }
        assertThat(TOURNAMENT_HITS.get() - before).isEqualTo(6);
        assertThat(output).contains("Retrying GET /api/tournaments on route tournament (retry 1 of 2) after ");
        assertThat(output).contains("ConnectException");
    }

    @Test
    void getCutAtTheReadTimeout_isNotRetried() throws Exception {
        // The replica got the request and is slow: a second read would double
        // its load and could not fit in the time limit anyway.
        int before = SLOW_READ_HITS.get();

        HttpResponse<String> response = send(get(port, "/api/users/slow", userToken()));

        assertThat(response.statusCode()).isEqualTo(503);
        assertEnvelope(response, 503, "DOWNSTREAM_UNAVAILABLE", "/api/users/slow");
        assertThat(SLOW_READ_HITS.get() - before).isEqualTo(1);
    }

    @Test
    void downstream500_isPassedThroughAndNotRetried() throws Exception {
        int before = BROKEN_HITS.get();

        HttpResponse<String> response = send(get(port, "/api/auth/broken", userToken()));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("INTERNAL_ERROR");
        assertThat(BROKEN_HITS.get() - before).isEqualTo(1);
    }
}
