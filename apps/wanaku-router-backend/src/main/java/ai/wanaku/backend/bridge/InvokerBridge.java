package ai.wanaku.backend.bridge;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
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
    private final Vertx vertx;

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

    @Inject
    public InvokerBridge(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            EventNotifier eventNotifier,
            Vertx vertx) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.eventNotifier = eventNotifier;
        this.vertx = vertx;
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

        String requestId = toolArguments.requestId().asString();
        String connectionId = toolArguments.connection().id();

        return Uni.createFrom()
                .item(() -> WanakuToolContext.create(toolArguments, ref))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .invoke(ctx -> {
                    if (vertx != null) {
                        vertx.runOnContext(v -> RequestIdContext.setContext(requestId, connectionId));
                    } else {
                        RequestIdContext.setContext(requestId, connectionId);
                    }
                })
                .invoke(ctx -> RequestIdContext.setToolName(ref.getName()))
                .invoke(this::resolveService)
                .invoke(ctx -> {
                    ctx.request = InvokerToolExecutor.buildToolInvokeRequest(ref, toolArguments, requestId);
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
                        .recoverWithItem(failure -> {
                            emitFailed(ctx, failure);

                            LOG.debugf(failure, "Handling failure: %s", failure.getMessage());
                            return ToolResponse.error(failure.getMessage());
                        }))
                .onFailure()
                .recoverWithItem(failure -> {
                    LOG.debugf(failure, "Pre-invocation failure: %s", failure.getMessage());
                    return ToolResponse.error(failure.getMessage());
                })
                .onItemOrFailure()
                .invoke((item, failure) -> {
                    if (vertx != null) {
                        vertx.runOnContext(v -> RequestIdContext.clear());
                    } else {
                        RequestIdContext.clear();
                    }
                });
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
                        "There is no host registered for service %s".formatted(context.toolReference.getType()));
            }
        }
        return context;
    }
}
