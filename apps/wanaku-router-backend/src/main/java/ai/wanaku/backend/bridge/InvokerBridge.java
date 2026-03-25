package ai.wanaku.backend.bridge;

import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

/**
 * A proxy class for invoking tools via gRPC.
 * <p>
 * This proxy is responsible for executing tool invocations. It delegates
 * gRPC transport operations to {@link GrpcTransport}, separating business
 * logic from transport concerns.
 */
public class InvokerBridge implements ToolsBridge {
    private static final Logger LOG = Logger.getLogger(InvokerBridge.class);
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE__TYPE_CODE_EXECUTION_ENGINE = ServiceType.CODE_EXECUTION_ENGINE.asValue();

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final EventNotifier eventNotifier;

    static class WanakuToolContext {
        ToolManager.ToolArguments arguments;
        ToolReference toolReference;
        ServiceTarget serviceTarget;
        ToolInvokeRequest request;
        ToolCallEvent startedEvent;
        Instant startTime;

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
     * Creates a new InvokerBridge with the specified service resolver, transport, and event notifier.
     *
     * @param serviceResolver the resolver for locating tool services
     * @param transport the gRPC transport for communication
     * @param eventNotifier the notifier for tool call events (nullable)
     */
    public InvokerBridge(
            ServiceResolver serviceResolver, WanakuBridgeTransport transport, EventNotifier eventNotifier) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Uni<ToolResponse> execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
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
                .invoke(this::resolveService)
                .invoke(ctx -> {
                    ctx.request = InvokerToolExecutor.buildToolInvokeRequest(ref, toolArguments);
                    ctx.startTime = Instant.now();
                    if (eventNotifier != null) {
                        ctx.startedEvent =
                                eventNotifier.emitStartedEvent(toolArguments, ref, ctx.serviceTarget, ctx.request);
                    }
                })
                .chain(ctx -> transport
                        .invokeTool(ctx.request, ctx.serviceTarget)
                        .invoke(response -> emitCompleted(ctx, response))
                        .onFailure()
                        .invoke(failure -> emitFailed(ctx, failure)));
    }

    private void emitCompleted(WanakuToolContext ctx, ToolResponse response) {
        if (eventNotifier != null && ctx.startedEvent != null) {
            long duration = Duration.between(ctx.startTime, Instant.now()).toMillis();
            String content = response != null ? response.toString() : "";
            eventNotifier.emitCompletedEvent(ctx.startedEvent.getEventId(), content, duration);
        }
    }

    private void emitFailed(WanakuToolContext ctx, Throwable failure) {
        if (eventNotifier != null && ctx.startedEvent != null) {
            long duration = Duration.between(ctx.startTime, Instant.now()).toMillis();
            ToolCallEvent.ErrorCategory category = failure instanceof Exception ex
                    ? eventNotifier.categorizeException(ex)
                    : ToolCallEvent.ErrorCategory.UNKNOWN;
            String errorMessage =
                    failure.getMessage() != null ? failure.getMessage() : "An error occurred during tool execution";
            eventNotifier.emitFailedEvent(ctx.startedEvent.getEventId(), category, errorMessage, duration);
        }
    }

    private WanakuToolContext resolveService(WanakuToolContext context) {
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
