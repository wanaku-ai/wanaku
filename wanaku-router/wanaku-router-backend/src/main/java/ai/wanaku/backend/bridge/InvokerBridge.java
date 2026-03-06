package ai.wanaku.backend.bridge;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

/**
 * A proxy class for invoking tools via gRPC.
 * <p>
 * This proxy is responsible for provisioning tool configurations and
 * executing tool invocations. It delegates gRPC transport operations to
 * {@link GrpcTransport}, separating business logic from transport concerns.
 */
public class InvokerBridge implements ToolsBridge {
    private static final Logger LOG = Logger.getLogger(InvokerBridge.class);
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE__TYPE_CODE_EXECUTION_ENGINE = ServiceType.CODE_EXECUTION_ENGINE.asValue();

    @Inject
    ServiceResolver serviceResolver;

    @Inject
    WanakuBridgeTransport transport;

    private final InvokerToolExecutor executor;
    private final ToolResponseTransformer<ToolInvokeReply> responseTransformer;

    static class WanakuToolContext {
        ToolManager.ToolArguments arguments;
        ToolReference toolReference;
        ServiceTarget serviceTarget;
        ToolInvokeRequest request;

        static WanakuToolContext create(ToolManager.ToolArguments arguments, ToolReference toolReference) {
            WanakuToolContext context = new WanakuToolContext();
            context.arguments = arguments;
            context.toolReference = toolReference;
            return context;
        }
    }

    /**
     * Creates a new InvokerBridge with the specified service resolver and transport.
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
        this.responseTransformer = transport.newToolResponseTransformer();
    }

    @Override
    @Deprecated
    public ToolResponse execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        return executor.execute(toolArguments, toolReference);
    }

    @Override
    public Uni<ToolResponse> executeAsync(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        if (!(toolReference instanceof ToolReference ref)) {
            LOG.errorf(
                    "Tool reference %s not supported",
                    toolReference == null ? "null" : toolReference.getClass().getName());
            return Uni.createFrom()
                    .failure(new UnsupportedOperationException(
                            "Only local tool call references should be invoked by this executor"));
        }

        return Uni.createFrom()
                .item(() -> WanakuToolContext.create(toolArguments, ref))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .invoke(this::resolveServiceV2)
                .invoke(ctx -> ctx.request = InvokerToolExecutor.buildToolInvokeRequest(ref, toolArguments))
                .chain(ctx -> transport
                        .invokeToolAsync(ctx.request, ctx.serviceTarget)
                        .map(responseTransformer::transformReply));
    }

    @Override
    public ProvisioningReference provision(ToolPayload toolPayload) {
        ToolReference toolReference = toolPayload.getPayload();

        LOG.debugf("Provisioning tool: %s (type: %s)", toolReference.getName(), toolReference.getType());

        ServiceTarget service = resolveService(toolReference.getType(), SERVICE_TYPE_TOOL_INVOKER);

        return transport.provision(
                toolReference.getName(), toolPayload.getConfigurationData(), toolPayload.getSecretsData(), service);
    }

    private ServiceTarget resolveService(String type, String serviceType) {
        LOG.debugf("Resolving service for type '%s' and service type '%s'", type, serviceType);
        ServiceTarget service = serviceResolver.resolve(type, serviceType);
        if (service == null) {
            throw new ServiceNotFoundException("There is no host registered for service " + type);
        }
        LOG.debugf("Resolved service: %s", service.toAddress());
        return service;
    }

    private WanakuToolContext resolveServiceV2(WanakuToolContext context) {
        context.serviceTarget = serviceResolver.resolve(context.toolReference.getType(), SERVICE_TYPE_TOOL_INVOKER);
        if (context.serviceTarget == null) {
            // Code engines may also provide specialized tools
            context.serviceTarget =
                    serviceResolver.resolve(context.toolReference.getType(), SERVICE__TYPE_CODE_EXECUTION_ENGINE);
            if (context.serviceTarget == null) {
                throw new ServiceNotFoundException(
                        "There is no host registered for service " + context.toolReference.getType());
            }
        }
        return context;
    }
}
