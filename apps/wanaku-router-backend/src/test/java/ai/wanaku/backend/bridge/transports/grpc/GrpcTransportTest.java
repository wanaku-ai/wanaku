package ai.wanaku.backend.bridge.transports.grpc;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import ai.wanaku.backend.support.MockGrpcCapabilityServer;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcTransportTest {

    private MockGrpcCapabilityServer server;
    private ServiceTarget target;
    private GrpcTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockGrpcCapabilityServer(List.of("ok"));
        int port = server.start();
        target = new ServiceTarget(
                "test-id", "test-service", "localhost", port, "tool-invoker", "mcp", null, null, null);
        transport = new GrpcTransport();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void provisionAsync_usesNonBlockingStub() {
        var reference = transport
                .provisionAsync("test", "config", "secret", target)
                .await()
                .atMost(Duration.ofSeconds(5));

        assertEquals("file:///tmp/mock-config", reference.configurationURI().toString());
        assertEquals("file:///tmp/mock-secret", reference.secretsURI().toString());
    }

    @Test
    void probeHealthAsync_usesNonBlockingStub() {
        var request = HealthProbeRequest.newBuilder().setId(target.getId()).build();

        var reply = transport.probeHealthAsync(request, target).await().atMost(Duration.ofSeconds(5));

        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, reply.getStatus());
    }

    @Test
    void provisionAsync_mapsInvalidArgumentError() {
        server.setProvisionException(
                new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("invalid config")));

        var thrown = assertThrows(WanakuException.class, () -> transport
                .provisionAsync("test", "config", "secret", target)
                .await()
                .atMost(Duration.ofSeconds(5)));

        assertTrue(thrown.getMessage().contains("invalid config"));
    }

    @Test
    void probeHealthAsync_mapsUnavailableError() {
        server.setProbeHealthException(
                new StatusRuntimeException(Status.UNAVAILABLE.withDescription("service unavailable")));

        var request = HealthProbeRequest.newBuilder().setId(target.getId()).build();

        var thrown = assertThrows(
                ServiceUnavailableException.class,
                () -> transport.probeHealthAsync(request, target).await().atMost(Duration.ofSeconds(5)));

        assertTrue(thrown.getMessage().contains("not available"));
    }

    @Test
    void probeHealthAsync_mapsDeadlineExceededError() {
        server.setProbeHealthException(
                new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("deadline exceeded")));

        var request = HealthProbeRequest.newBuilder().setId(target.getId()).build();

        var thrown = assertThrows(
                ServiceUnavailableException.class,
                () -> transport.probeHealthAsync(request, target).await().atMost(Duration.ofSeconds(5)));

        assertTrue(thrown.getMessage().contains("did not respond within a reasonable time frame"));
    }
}
