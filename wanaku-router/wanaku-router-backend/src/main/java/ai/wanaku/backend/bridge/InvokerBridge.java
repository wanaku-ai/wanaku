package ai.wanaku.backend.bridge;

import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.common.ToolExecutor;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.jboss.logging.Logger;

/**
 * A proxy class for invoking tools via gRPC.
 * <p>
 * This proxy is responsible for provisioning tool configurations and
 * providing access to a tool executor. The actual tool execution logic
 * is delegated to {@link InvokerToolExecutor} through composition,
 * separating proxy management from execution concerns.
 * <p>
 * This class uses composition to delegate gRPC transport operations to
 * {@link GrpcTransport}, separating business logic from transport concerns.
 * It focuses solely on tool-specific concerns while delegating infrastructure
 * concerns to the transport layer.
 */
public class InvokerBridge implements ToolsBridge {
    private static final Logger LOG = Logger.getLogger(InvokerBridge.class);
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final ToolExecutor executor;

    /**
     * Creates a new InvokerBridge with the specified service resolver and transport.
     * <p>
     * This constructor is primarily intended for testing purposes, allowing
     * injection of a mock or custom transport implementation.
     *
     * @param serviceResolver the resolver for locating tool services
     * @param transport the gRPC transport for communication
     */
    public InvokerBridge(ServiceResolver serviceResolver, WanakuBridgeTransport transport) {
        this(serviceResolver, transport, null);
    }

    /**
     * Creates a new InvokerBridge with the specified service resolver, transport, and event emitter.
     *
     * @param serviceResolver the resolver for locating tool services
     * @param transport the gRPC transport for communication
     * @param toolCallEventEmitter the emitter for tool call events (nullable)
     */
    public InvokerBridge(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            MutinyEmitter<ToolCallEvent> toolCallEventEmitter) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.executor = new InvokerToolExecutor(serviceResolver, transport, toolCallEventEmitter);
    }

    @Override
    public ToolExecutor getExecutor() {
        return executor;
    }

    @Override
    public ProvisioningReference provision(ToolPayload toolPayload) {
        ToolReference toolReference = toolPayload.getPayload();

        LOG.debugf("Provisioning tool: %s (type: %s)", toolReference.getName(), toolReference.getType());

        ServiceTarget service = resolveService(toolReference.getType(), SERVICE_TYPE_TOOL_INVOKER);

        return transport.provision(
                toolReference.getName(), toolPayload.getConfigurationData(), toolPayload.getSecretsData(), service);
    }

    /**
     * Resolves a service target for the specified type and service type.
     * <p>
     * This method uses the service resolver to locate the appropriate service
     * and throws an exception if no service is found.
     *
     * @param type the service type identifier
     * @param serviceType the category of service (e.g., "tool-invoker", "resource-provider")
     * @return the resolved service target
     * @throws ServiceNotFoundException if no service is registered for the given type
     */
    private ServiceTarget resolveService(String type, String serviceType) {
        LOG.debugf("Resolving service for type '%s' and service type '%s'", type, serviceType);
        ServiceTarget service = serviceResolver.resolve(type, serviceType);
        if (service == null) {
            throw new ServiceNotFoundException("There is no host registered for service " + type);
        }
        LOG.debugf("Resolved service: %s", service.toAddress());
        return service;
    }
}
