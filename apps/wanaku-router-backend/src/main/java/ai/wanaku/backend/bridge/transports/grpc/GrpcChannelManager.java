package ai.wanaku.backend.bridge.transports.grpc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
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
 * By default channels use plaintext communication; TLS can be enabled via
 * {@code wanaku.bridge.grpc.transport.tls.enabled}. Future enhancements may include:
 * <ul>
 *   <li>Connection pooling for reusing channels</li>
 *   <li>Per-target TLS trust material configuration</li>
 *   <li>Retry policies and circuit breakers</li>
 * </ul>
 */
@ApplicationScoped
public class GrpcChannelManager {
    private static final Logger LOG = Logger.getLogger(GrpcChannelManager.class);
    private static final Map<ServiceTarget, ManagedChannel> CHANNEL_MAP = new ConcurrentHashMap<>();
    private static final String WANAKU_BRIDGE_GRPC_TRANSPORT_TLS_ENABLED = "wanaku.bridge.grpc.transport.tls.enabled";

    /**
     * Whether to secure the channel with TLS. Defaults to {@code false} (plaintext), which is only
     * appropriate when the router and capabilities share a trusted (in-cluster / localhost) network.
     */
    private final boolean tlsEnabled = ConfigProvider.getConfig()
            .getOptionalValue(WANAKU_BRIDGE_GRPC_TRANSPORT_TLS_ENABLED, Boolean.class)
            .orElse(false);

    private final ClientInterceptor otelInterceptor;
    private final RequestIdClientInterceptor requestIdInterceptor;

    /**
     * CDI constructor. Injects OpenTelemetry and RequestIdClientInterceptor to register
     * tracing and request-id propagation on all outgoing gRPC channels.
     *
     * @param openTelemetry the OpenTelemetry instance for creating gRPC tracing interceptors
     * @param requestIdInterceptor the interceptor that propagates Wanaku request IDs via gRPC metadata
     */
    @Inject
    GrpcChannelManager(OpenTelemetry openTelemetry, RequestIdClientInterceptor requestIdInterceptor) {
        this.otelInterceptor = GrpcTelemetry.create(openTelemetry).newClientInterceptor();
        this.requestIdInterceptor = requestIdInterceptor;
    }

    /**
     * No-arg constructor for testing or non-CDI usage. No interceptors are registered.
     */
    GrpcChannelManager() {
        this.otelInterceptor = null;
        this.requestIdInterceptor = null;
    }

    /**
     * Creates a new gRPC channel for the specified service target.
     * <p>
     * The channel uses plaintext or TLS depending on
     * {@code wanaku.bridge.grpc.transport.tls.enabled}. The caller is responsible for managing the
     * channel lifecycle, including shutdown.
     *
     * @param service the service target containing the address to connect to
     * @return a new ManagedChannel configured for the service
     */
    public ManagedChannel createChannel(ServiceTarget service) {
        if (CHANNEL_MAP.containsKey(service)) {
            LOG.debugf("Reusing gRPC channel for service: %s", service.toAddress());
            return CHANNEL_MAP.get(service);
        }

        LOG.debugf("Creating gRPC channel for service: %s (TLS: %s)", service.toAddress(), tlsEnabled);
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(service.toAddress());
        if (tlsEnabled) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        if (otelInterceptor != null) {
            builder.intercept(otelInterceptor);
        }
        if (requestIdInterceptor != null) {
            builder.intercept(requestIdInterceptor);
        }

        ManagedChannel channel = builder.build();

        CHANNEL_MAP.put(service, channel);
        return channel;
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
     * Closes a gRPC channel gracefully.
     * <p>
     * This method attempts to shutdown the channel. If an error occurs during
     * shutdown, it is logged but not propagated to avoid disrupting the caller.
     *
     * @param channel the channel to close, may be null
     */
    public void closeChannel(ManagedChannel channel) {
        // NO-OP
    }

    public void shutdown() {
        for (Map.Entry<ServiceTarget, ManagedChannel> entry : CHANNEL_MAP.entrySet()) {
            doCloseChannel(entry.getValue());
        }
    }
}
