package ai.wanaku.backend.bridge.transports.grpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Manages the lifecycle of gRPC channels for proxy implementations.
 * <p>
 * This class is responsible for creating and managing gRPC channels with
 * consistent configuration across all proxy implementations. It provides
 * a centralized point for channel management, making it easier to add
 * features like connection pooling, interceptors, and custom configurations
 * in the future.
 * <p>
 * Channels are cached per service endpoint and reused across requests.
 */
class GrpcChannelManager {
    private static final Logger LOG = Logger.getLogger(GrpcChannelManager.class);
    private static final Map<String, ManagedChannel> CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * Returns a cached gRPC channel for the specified service target.
     * <p>
     * The channel is configured with plaintext communication and reused for
     * subsequent requests to the same endpoint.
     *
     * @param service the service target containing the address to connect to
     * @return a cached ManagedChannel configured for the service
     */
    public ManagedChannel createChannel(ServiceTarget service) {
        String target = service.toAddress();
        return CHANNEL_MAP.computeIfAbsent(target, endpoint -> {
            LOG.debugf("Creating gRPC channel for service: %s", endpoint);
            return ManagedChannelBuilder.forTarget(endpoint)
                    .usePlaintext()
                    .build();
        });
    }

    /**
     * Closes a gRPC channel gracefully.
     * <p>
     * This method attempts to shutdown the channel. If an error occurs during
     * shutdown, it is logged but not propagated to avoid disrupting the caller.
     *
     * @param channel the channel to close, may be null
     */
    public void doCloseChannel(ManagedChannel channel) {
        if (channel != null && !channel.isShutdown()) {
            try {
                LOG.debugf("Closing gRPC channel");
                channel.shutdown();
            } catch (Exception e) {
                LOG.warnf(e, "Error closing gRPC channel");
            }
        }
    }

    /**
     * No-op for request-level cleanup.
     * <p>
     * Channels are cached and reused, so they are not closed after each request.
     *
     * @param channel the channel to close, may be null
     */
    public void closeChannel(ManagedChannel channel) {
        // NO-OP
    }

    /**
     * Shuts down and removes all cached channels.
     */
    public void shutdown() {
        for (Map.Entry<String, ManagedChannel> entry : CHANNEL_MAP.entrySet()) {
            doCloseChannel(entry.getValue());
        }
        CHANNEL_MAP.clear();
    }
}
