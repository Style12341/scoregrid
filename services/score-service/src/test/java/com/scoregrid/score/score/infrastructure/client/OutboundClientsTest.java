package com.scoregrid.score.score.infrastructure.client;

import com.scoregrid.score.TestcontainersConfiguration;
import com.scoregrid.score.score.domain.port.out.PredictionClientPort.PredictionResult;
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
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.scoregrid.score.shared.config.ResilienceConfig.PREDICTION_CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * score-service's calls to auth-service and prediction-service when a replica
 * is dead, on the production timeouts (500 ms connect, 1.5 s read).
 *
 * <p>A dead replica is an address that never completes the TCP handshake,
 * like a SIGKILLed container's. auth-service has only that one.
 * prediction-service also has an in-process stub, and round robin alternates
 * between the two, so after the first call every call tries the dead one
 * first.
 *
 * <p>Wall-clock bounds are loose on purpose; results, hit counters and log
 * lines are the real assertions.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class OutboundClientsTest {

    private static final @Nullable Blackhole DEAD = Blackhole.open();
    private static final HttpServer STUB = startStub();
    private static final AtomicInteger PREDICTION_HITS = new AtomicInteger();

    /** How long the stub takes to answer, set per test. */
    private static volatile long predictionDelayMillis = 0;

    static {
        STUB.createContext("/api/predictions/match/", exchange -> {
            PREDICTION_HITS.incrementAndGet();
            sleep(predictionDelayMillis);
            respond(exchange, 200, """
                    [{"id":"p1","userId":"1","tournamentId":"t1","matchId":"m1","predictionType":"EXACT_SCORE",
                      "homeScore":2,"awayScore":1,"derivedOutcome":"HOME_WIN","locked":true,
                      "createdAt":"2026-10-01T12:00:00Z","updatedAt":"2026-10-01T12:00:00Z"}]""");
        });
    }

    @BeforeAll
    static void requireAConnectThatHangs() {
        assumeTrue(DEAD != null, "this OS refuses a connect to a full accept queue instead of dropping it, "
                + "so there is no dead replica to fail over from; skipped");
    }

    @DynamicPropertySource
    static void replicas(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> DEAD.uri());
        registry.add("spring.cloud.discovery.client.simple.instances.prediction-service[0].uri", () -> DEAD.uri());
        registry.add("spring.cloud.discovery.client.simple.instances.prediction-service[1].uri",
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
    AuthRestClient authClient;

    @Autowired
    PredictionRestClient predictionClient;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * The first request to a service builds its LoadBalancer context, which
     * takes long enough under a busy build to blur the timings below.
     */
    @BeforeEach
    void warmUp() {
        authClient.getUsernames(List.of("1"));
        predictionClient.getPredictionsForMatch("m1");
    }

    @AfterEach
    void reset() {
        predictionDelayMillis = 0;
        circuitBreakerRegistry.find(PREDICTION_CLIENT).ifPresent(CircuitBreaker::reset);
    }

    @Test
    void deadAuth_timesOutQuickly_andTheRankingGetsIdsForNames(CapturedOutput output) {
        long start = System.nanoTime();
        Map<String, String> names = authClient.getUsernames(List.of("1", "2"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of("1", "1", "2", "2"));
        // The 500 ms connect timeout from ClientConfig. Without it the call
        // waits for the OS to give up on the handshake: minutes.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        assertThat(output).contains("Failed to resolve usernames for 2 users");
    }

    @Test
    void slowButHealthyPrediction_isNotCutAtResilience4jsOneSecondDefault() {
        // Past the 1 s default on its own: these calls only succeed if the
        // configured 2.2 s limit is the one in force.
        predictionDelayMillis = 1100;
        int before = PREDICTION_HITS.get();

        for (int i = 0; i < 4; i++) {
            assertThat(predictionClient.getPredictionsForMatch("m1")).as("call #%d", i + 1)
                    .containsExactly(new PredictionResult("1", "p1", 2, 1));
        }
        // One request per call reached the live replica: nothing was replayed on it.
        assertThat(PREDICTION_HITS.get() - before).isEqualTo(4);
    }

    @Test
    void deadPredictionReplica_costsOneConnectTimeout_thenTheRetryGoesToTheLiveOne(CapturedOutput output) {
        for (int i = 0; i < 4; i++) {
            long start = System.nanoTime();
            List<PredictionResult> predictions = predictionClient.getPredictionsForMatch("m1");
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(predictions).as("call #%d", i + 1)
                    .containsExactly(new PredictionResult("1", "p1", 2, 1));
            // About 0.7 s: 500 ms connect timeout + 200 ms back-off + a fast read.
            assertThat(elapsed).as("call #%d", i + 1).isLessThan(Duration.ofMillis(2000));
        }
        assertThat(output).contains("Retrying predictionClient request (retry 1 of 2) after ");
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
