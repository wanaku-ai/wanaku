package ai.wanaku.backend.bridge.transports.grpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.grpc.ManagedChannel;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

class GrpcChannelManagerTest {

    private final GrpcChannelManager channelManager = new GrpcChannelManager();

    @AfterEach
    void tearDown() {
        channelManager.shutdown();
    }

    @Test
    void createChannel_reusesChannelForSameEndpoint() {
        ServiceTarget firstTarget = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);
        ServiceTarget secondTarget = new ServiceTarget(
                "id-2", "service-b", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel firstChannel = channelManager.createChannel(firstTarget);
        ManagedChannel secondChannel = channelManager.createChannel(secondTarget);

        assertSame(firstChannel, secondChannel);
        assertFalse(firstChannel.isShutdown());
    }

    @Test
    void createChannel_returnsSameInstanceForRepeatedCallsWithSameTarget() {
        ServiceTarget target = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel firstChannel = channelManager.createChannel(target);
        ManagedChannel secondChannel = channelManager.createChannel(target);
        ManagedChannel thirdChannel = channelManager.createChannel(target);

        assertSame(firstChannel, secondChannel);
        assertSame(secondChannel, thirdChannel);
    }

    @Test
    void createChannel_reusesSameChannelForEquivalentTargets() {
        ServiceTarget firstTarget = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);
        ServiceTarget secondTarget = new ServiceTarget(
                "id-2", "service-b", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel firstChannel = channelManager.createChannel(firstTarget);
        ManagedChannel secondChannel = channelManager.createChannel(secondTarget);

        assertSame(firstChannel, secondChannel);
    }

    @Test
    void createChannel_createsDifferentChannelsForDifferentEndpoints() {
        ServiceTarget firstTarget = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);
        ServiceTarget secondTarget = new ServiceTarget(
                "id-2", "service-b", "localhost", 9002, "tool-invoker", "mcp", null, null, null);

        ManagedChannel firstChannel = channelManager.createChannel(firstTarget);
        ManagedChannel secondChannel = channelManager.createChannel(secondTarget);

        assertNotSame(firstChannel, secondChannel);
    }

    @Test
    void closeChannel_doesNotShutdownCachedChannel() {
        ServiceTarget target = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel channel = channelManager.createChannel(target);
        channelManager.closeChannel(channel);

        assertFalse(channel.isShutdown());
        assertSame(channel, channelManager.createChannel(target));
    }

    @Test
    void closeChannel_acceptsNullWithoutThrowing() {
        assertDoesNotThrow(() -> channelManager.closeChannel(null));
    }

    @Test
    void shutdown_closesAndClearsCachedChannels() {
        ServiceTarget target = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel firstChannel = channelManager.createChannel(target);
        channelManager.shutdown();

        assertTrue(firstChannel.isShutdown());

        ManagedChannel secondChannel = channelManager.createChannel(target);
        assertNotSame(firstChannel, secondChannel);
    }

    @Test
    void shutdown_isIdempotent() {
        ServiceTarget target = new ServiceTarget(
                "id-1", "service-a", "localhost", 9001, "tool-invoker", "mcp", null, null, null);

        ManagedChannel channel = channelManager.createChannel(target);

        channelManager.shutdown();
        channelManager.shutdown();

        assertTrue(channel.isShutdown());
    }

    @Test
    void shutdown_onEmptyCacheDoesNotThrow() {
        GrpcChannelManager emptyManager = new GrpcChannelManager();

        assertDoesNotThrow(emptyManager::shutdown);
    }
}
