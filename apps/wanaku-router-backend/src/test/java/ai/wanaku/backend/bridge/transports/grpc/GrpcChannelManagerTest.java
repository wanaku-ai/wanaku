package ai.wanaku.backend.bridge.transports.grpc;

import io.grpc.ManagedChannel;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcChannelManagerTest {

    private GrpcChannelManager manager;

    @BeforeEach
    void setUp() {
        manager = new GrpcChannelManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void createChannel_reusesSameChannel() {
        ServiceTarget target =
                new ServiceTarget("id-1", "svc-a", "localhost", 9090, "tool-invoker", "mcp", null, null, null);

        ManagedChannel first = manager.createChannel(target);
        ManagedChannel second = manager.createChannel(target);

        assertSame(first, second);
    }

    @Test
    void createChannel_differentTargets_differentChannels() {
        ServiceTarget targetA =
                new ServiceTarget("id-1", "svc-a", "localhost", 9090, "tool-invoker", "mcp", null, null, null);
        ServiceTarget targetB =
                new ServiceTarget("id-2", "svc-b", "localhost", 9091, "resource-provider", "mcp", null, null, null);

        ManagedChannel channelA = manager.createChannel(targetA);
        ManagedChannel channelB = manager.createChannel(targetB);

        assertNotSame(channelA, channelB);
    }

    @Test
    void createChannel_replacesStaleChannel() {
        ServiceTarget target =
                new ServiceTarget("id-1", "svc-a", "localhost", 9090, "tool-invoker", "mcp", null, null, null);

        ManagedChannel original = manager.createChannel(target);
        original.shutdownNow();

        ManagedChannel replacement = manager.createChannel(target);

        assertNotSame(original, replacement);
        assertTrue(original.isShutdown());
    }

    @Test
    void shutdown_closesAllChannels() {
        ServiceTarget targetA =
                new ServiceTarget("id-1", "svc-a", "localhost", 9090, "tool-invoker", "mcp", null, null, null);
        ServiceTarget targetB =
                new ServiceTarget("id-2", "svc-b", "localhost", 9091, "resource-provider", "mcp", null, null, null);

        ManagedChannel channelA = manager.createChannel(targetA);
        ManagedChannel channelB = manager.createChannel(targetB);

        manager.shutdown();

        assertTrue(channelA.isShutdown());
        assertTrue(channelB.isShutdown());
    }
}
