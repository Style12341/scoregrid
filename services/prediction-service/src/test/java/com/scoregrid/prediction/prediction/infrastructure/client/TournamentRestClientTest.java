package com.scoregrid.prediction.prediction.infrastructure.client;

import com.scoregrid.prediction.TestcontainersConfiguration;
import com.scoregrid.prediction.shared.error.DomainException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.scoregrid.prediction.shared.config.ResilienceConfig.TOURNAMENT_CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The prediction -> tournament call while one tournament replica is dead:
 * the live demo's failover scenario C, without Docker for the downstream.
 *
 * <p>Two "replicas" behind {@code http://tournament-service}: one that never
 * completes the TCP handshake, like a SIGKILLed container's address, and an
 * in-process stub. Round robin alternates between them, so after the first
 * call every call tries the dead one first.
 *
 * <p>Production timeouts (500 ms connect, 1.5 s read) and time limit (2.2 s).
 *
 * <p>Wall-clock bounds are loose on purpose; hit counters and log lines are
 * the real assertions.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TournamentRestClientTest {

    private static final @Nullable Blackhole DEAD = Blackhole.open();
    private static final HttpServer STUB = startStub();
    private static final AtomicInteger SLOW_HITS = new AtomicInteger();
    private static final AtomicInteger FAST_HITS = new AtomicInteger();

    static {
        STUB.createContext("/api/tournaments/slow/participants/", exchange -> {
            SLOW_HITS.incrementAndGet();
            // Past Resilience4J's 1 s default time limiter on its own: these
            // calls only succeed if the configured limit is the one in force.
            sleep(1100);
            respond(exchange, 200, "{}");
        });
        STUB.createContext("/api/tournaments/fast/participants/", exchange -> {
            FAST_HITS.incrementAndGet();
            respond(exchange, 200, "{}");
        });
        STUB.createContext("/api/tournaments/broken/participants/", exchange ->
                respond(exchange, 500, "{\"error\":\"INTERNAL_ERROR\"}"));
        STUB.createContext("/api/matches/", exchange ->
                respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}"));
    }

    @BeforeAll
    static void requireAConnectThatHangs() {
        assumeTrue(DEAD != null, "this OS refuses a connect to a full accept queue instead of dropping it, "
                + "so there is no dead replica to fail over from; skipped");
    }

    @DynamicPropertySource
    static void tournamentReplicas(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.tournament-service[0].uri", () -> DEAD.uri());
        registry.add("spring.cloud.discovery.client.simple.instances.tournament-service[1].uri",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstreams() {
        STUB.stop(0);
        if (DEAD != null) {
            DEAD.close();
        }
    }

    @Autowired
    TournamentRestClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * The first request to a service builds its LoadBalancer context, which
     * takes long enough under a busy build to blur the timings below.
     */
    @BeforeEach
    void warmUp() {
        client.isUserEnrolled("fast", "42");
    }

    @AfterEach
    void closeBreaker() {
        circuitBreakerRegistry.find(TOURNAMENT_CLIENT).ifPresent(CircuitBreaker::reset);
    }

    @Test
    void slowButHealthyReplica_isNotCutAtResilience4jsOneSecondDefault() {
        int before = SLOW_HITS.get();

        for (int i = 0; i < 4; i++) {
            assertThat(client.isUserEnrolled("slow", "42")).as("call #%d", i + 1).isTrue();
        }
        // One request per call reached the live replica: nothing was replayed on it.
        assertThat(SLOW_HITS.get() - before).isEqualTo(4);
    }

    @Test
    void deadReplica_costsOneConnectTimeout_thenTheRetryGoesToTheLiveOne(CapturedOutput output) {
        for (int i = 0; i < 4; i++) {
            long start = System.nanoTime();
            assertThat(client.isUserEnrolled("fast", "42")).as("call #%d", i + 1).isTrue();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            // About 0.7 s: 500 ms connect timeout + 200 ms back-off + a fast
            // read. Without the connect timeout the attempt hangs until the
            // time limiter cuts it and the call fails, whatever the clock says.
            assertThat(elapsed).as("call #%d", i + 1).isLessThan(Duration.ofMillis(2000));
        }
        assertThat(output).contains("Retrying tournamentClient request (retry 1 of 2) after ");
    }

    @Test
    void unknownMatches_doNotOpenTheBreaker() {
        CircuitBreaker breaker = circuitBreakerRegistry.find(TOURNAMENT_CLIENT).orElseThrow();

        // As many 404s as the breaker's minimum number of calls: counted as
        // failures, they would open it at a 100% failure rate.
        for (int i = 0; i < 10; i++) {
            String matchId = "no-such-match-" + i;
            assertThatThrownBy(() -> client.getMatch(matchId))
                    .as(matchId)
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.errorCode()).isEqualTo("NOT_FOUND"));
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(client.isUserEnrolled("fast", "42")).isTrue();
    }

    @Test
    void downstreamFailure_isA503_andTheLogNamesTheCauseNotTheBreaker(CapturedOutput output) {
        assertThatThrownBy(() -> client.isUserEnrolled("broken", "42"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("DOWNSTREAM_UNAVAILABLE"));

        assertThat(output).contains("tournament-service call for enrolment broken/42 failed: ");
        assertThat(output).doesNotContain("circuit open");
    }

    @Test
    void openBreaker_failsFastWithoutCallingAndSaysSo(CapturedOutput output) {
        circuitBreakerRegistry.find(TOURNAMENT_CLIENT).orElseThrow().transitionToOpenState();
        int before = FAST_HITS.get();

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.isUserEnrolled("fast", "42"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("DOWNSTREAM_UNAVAILABLE"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // The counter is the proof that nothing was called; the clock only
        // rules out a hang.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
        assertThat(FAST_HITS.get()).isEqualTo(before);
        assertThat(output).contains("tournament-service not called for enrolment fast/42: circuit open");
    }

    // ── downstream doubles ────────────────────────────────────────────────

    /**
     * A listening socket whose accept queue is full, so the kernel drops any
     * further SYN and a connect hangs until the client gives up: what a
     * client sees when the replica behind an address is gone. A closed port
     * would refuse at once and never exercise the connect timeout.
     *
     * <p>Linux drops the SYN. Windows and macOS answer a full queue with RST,
     * so the connect is refused instead of hanging; {@link #open()} then
     * returns null and the class is skipped rather than failing to load.
     */
    private record Blackhole(ServerSocket server, List<Socket> backlog) {

        static @Nullable Blackhole open() {
            ServerSocket server = null;
            List<Socket> backlog = new ArrayList<>();
            try {
                server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                for (int i = 0; i < 10; i++) {
                    Socket socket = new Socket();
                    try {
                        socket.connect(server.getLocalSocketAddress(), 200);
                        backlog.add(socket);
                    }
                    catch (SocketTimeoutException full) {
                        socket.close();
                        return new Blackhole(server, backlog);
                    }
                }
            }
            catch (IOException refused) {
                // RST on a full queue: no connect will ever hang here.
            }
            new Blackhole(server, backlog).close();
            return null;
        }

        String uri() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void close() {
            try {
                for (Socket socket : backlog) {
                    socket.close();
                }
                if (server != null) {
                    server.close();
                }
            }
            catch (IOException ignored) {
                // test teardown
            }
        }
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
