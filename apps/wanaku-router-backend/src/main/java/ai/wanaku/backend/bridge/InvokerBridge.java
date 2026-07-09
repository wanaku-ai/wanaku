package ai.wanaku.backend.bridge;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

public class InvokerBridge implements ToolsBridge {
    private static final Logger LOG = Logger.getLogger(InvokerBridge.class);
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE__TYPE_CODE_EXECUTION_ENGINE = ServiceType.CODE_EXECUTION_ENGINE.asValue();

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final EventNotifier eventNotifier;
    private final Vertx vertx;

    static class WanakuToolContext {
        McpSchema.CallToolRequest callToolRequest;
        String sessionId;
        McpTransportContext transportContext;
        String requestId;
        ToolReference toolReference;
        ServiceTarget serviceTarget;
        ToolInvokeRequest request;
        ToolCallEvent startedEvent;
        Instant startTime;

        static WanakuToolContext create(
                McpSchema.CallToolRequest callToolRequest,
                String sessionId,
                McpTransportContext transportContext,
                ToolReference toolReference) {
            WanakuToolContext context = new WanakuToolContext();
            context.callToolRequest = callToolRequest;
            context.sessionId = sessionId;
            context.transportContext = transportContext;
            context.requestId = UUID.randomUUID().toString();
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
    public Uni<McpSchema.CallToolResult> execute(
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            McpTransportContext transportContext,
            CallableReference toolReference) {
        if (!(toolReference instanceof ToolReference ref)) {
            LOG.errorf(
                    "Tool reference %s not supported",
                    toolReference == null ? "null" : toolReference.getClass().getName());
            return Uni.createFrom()
                    .failure(new UnsupportedOperationException(
                            "Only local tool call references should be invoked by this executor"));
        }

        return Uni.createFrom()
                .item(() -> WanakuToolContext.create(callToolRequest, sessionId, transportContext, ref))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .invoke(ctx -> {
                    if (vertx != null) {
                        vertx.runOnContext(v -> RequestIdContext.setContext(ctx.requestId, ctx.sessionId));
                    } else {
                        RequestIdContext.setContext(ctx.requestId, ctx.sessionId);
                    }
                })
                .invoke(ctx -> RequestIdContext.setToolName(ref.getName()))
                .invoke(this::resolveService)
                .invoke(ctx -> {
                    ctx.request = InvokerToolExecutor.buildToolInvokeRequest(
                            ref, callToolRequest, ctx.requestId, ctx.transportContext);
                    ctx.startTime = Instant.now();
                    if (eventNotifier != null) {
                        ctx.startedEvent = eventNotifier.emitStartedEvent(
                                callToolRequest, ctx.sessionId, ref, ctx.serviceTarget, ctx.request);
                    }
                })
                .chain(ctx -> transport
                        .invokeTool(ctx.request, ctx.serviceTarget)
                        .invoke(response -> emitCompleted(ctx, response))
                        .onFailure()
                        .recoverWithItem(failure -> {
                            emitFailed(ctx, failure);

                            LOG.debugf(failure, "Handling failure: %s", failure.getMessage());
                            return McpSchema.CallToolResult.builder(java.util.List.of(
                                            (McpSchema.Content) McpSchema.TextContent.builder(failure.getMessage())
                                                    .build()))
                                    .isError(true)
                                    .build();
                        }))
                .onItemOrFailure()
                .invoke((item, failure) -> {
                    if (vertx != null) {
                        vertx.runOnContext(v -> RequestIdContext.clear());
                    } else {
                        RequestIdContext.clear();
                    }
                })
                .onFailure()
                .recoverWithItem(failure -> {
                    LOG.debugf(failure, "Handling failure: %s", failure.getMessage());
                    return ToolResponse.error(failure.getMessage());
                });
    }

    private void emitCompleted(WanakuToolContext ctx, McpSchema.CallToolResult response) {
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
