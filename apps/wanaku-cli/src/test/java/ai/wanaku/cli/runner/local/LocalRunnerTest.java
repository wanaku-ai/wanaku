package ai.wanaku.cli.runner.local;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalRunnerTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPollRouterReadinessUntilHealthy() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/q/health/ready", exchange -> {
            int status = requests.incrementAndGet() < 3 ? 503 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        URI readinessUri = URI.create("http://localhost:%d/q/health/ready"
                .formatted(server.getAddress().getPort()));

        LocalRunner runner = new LocalRunner(
                mock(WanakuCliConfig.class),
                new LocalRunner.LocalRunnerEnvironment(),
                HttpClient.newHttpClient(),
                readinessUri);

        assertDoesNotThrow(() -> runner.waitForRouterReadiness(Duration.ofSeconds(2), Duration.ofMillis(10)));
    }

    @Test
    void shouldFailWhenRouterDoesNotBecomeReady() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/q/health/ready", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        URI readinessUri = URI.create("http://localhost:%d/q/health/ready"
                .formatted(server.getAddress().getPort()));

        LocalRunner runner = new LocalRunner(
                mock(WanakuCliConfig.class),
                new LocalRunner.LocalRunnerEnvironment(),
                HttpClient.newHttpClient(),
                readinessUri);

        assertThrows(
                IOException.class, () -> runner.waitForRouterReadiness(Duration.ofMillis(50), Duration.ofMillis(10)));
    }

    @Test
    void shouldUseRemainingTimeoutForReadinessRequests() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/q/health/ready", exchange -> {
            LockSupport.parkNanos(Duration.ofMillis(500).toNanos());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        URI readinessUri = URI.create("http://localhost:%d/q/health/ready"
                .formatted(server.getAddress().getPort()));

        LocalRunner runner = new LocalRunner(
                mock(WanakuCliConfig.class),
                new LocalRunner.LocalRunnerEnvironment(),
                HttpClient.newHttpClient(),
                readinessUri);

        long startedAt = System.nanoTime();
        assertThrows(
                IOException.class, () -> runner.waitForRouterReadiness(Duration.ofMillis(100), Duration.ofMillis(10)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertTrue(elapsed.compareTo(Duration.ofSeconds(1)) < 0);
    }
}
