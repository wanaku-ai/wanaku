package ai.wanaku.backend.bridge.transports.grpc;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceManager;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import ai.wanaku.backend.support.MockGrpcCapabilityServer;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.ResourceRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        transport = new GrpcTransport(new GrpcChannelManager());
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

    @Test
    void invokeTool_usesNonBlockingStub() {
        var request = ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        var response = transport.invokeTool(request, target).await().atMost(Duration.ofSeconds(5));

        assertFalse(response.content().isEmpty());
    }

    @Test
    void acquireResource_usesNonBlockingStub() {
        var request = ResourceRequest.newBuilder()
                .setLocation("test://resource")
                .setName("test-resource")
                .build();
        var mcpResource = new ResourceReference();
        mcpResource.setMimeType("text/plain");

        var arguments = mock(ResourceManager.ResourceArguments.class);
        when(arguments.requestUri()).thenReturn(new RequestUri("test://resource"));

        var response = transport
                .acquireResource(request, target, arguments, mcpResource)
                .await()
                .atMost(Duration.ofSeconds(5));

        assertFalse(response.isEmpty());
    }

    @Test
    void invokeTool_cancellationCleansUp() {
        CountDownLatch latch = new CountDownLatch(1);
        server.setResponseLatch(latch);

        var request = ToolInvokeRequest.newBuilder().setUri("test://tool").build();
        var subscriber = transport.invokeTool(request, target).subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.cancel();
        latch.countDown();
        subscriber.assertNotTerminated();
    }

    @Test
    void acquireResource_cancellationCleansUp() {
        CountDownLatch latch = new CountDownLatch(1);
        server.setResponseLatch(latch);

        var request = ResourceRequest.newBuilder()
                .setLocation("test://resource")
                .setName("test-resource")
                .build();
        var mcpResource = new ResourceReference();
        mcpResource.setMimeType("text/plain");

        var arguments = mock(ResourceManager.ResourceArguments.class);
        when(arguments.requestUri()).thenReturn(new RequestUri("test://resource"));

        var subscriber = transport
                .acquireResource(request, target, arguments, mcpResource)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber.cancel();
        latch.countDown();
        subscriber.assertNotTerminated();
    }
}
