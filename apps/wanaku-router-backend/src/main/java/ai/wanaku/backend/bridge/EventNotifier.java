package ai.wanaku.backend.bridge;

import java.util.Map;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.CollectionsHelper;

/**
 * Responsible for emitting tool call lifecycle events (started, completed, failed)
 * to a reactive messaging channel for UI observability.
 */
public class EventNotifier {
    private static final Logger LOG = Logger.getLogger(EventNotifier.class);

    private final MutinyEmitter<ToolCallEvent> emitter;

    public EventNotifier(MutinyEmitter<ToolCallEvent> emitter) {
        this.emitter = emitter;
    }

    /**
     * Emits a STARTED event for a tool call.
     *
     * @param toolArguments the tool arguments
     * @param toolReference the tool reference
     * @param service the resolved service target
     * @param request the built tool invoke request
     * @return the started event (for tracking the eventId), or null if emission fails
     */
    public ToolCallEvent emitStartedEvent(
            ToolManager.ToolArguments toolArguments,
            ToolReference toolReference,
            ServiceTarget service,
            ToolInvokeRequest request) {
        try {
            Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(toolArguments.args());
            ToolCallEvent event = ToolCallEvent.started(
                    toolReference.getName(),
                    toolReference.getType(),
                    toolArguments.connection().id(),
                    service.getId(),
                    service.toAddress(),
                    argumentsMap,
                    request.getHeadersMap(),
                    request.getBody(),
                    request.getConfigurationUri(),
                    request.getSecretsUri());

            if (emitter.hasRequests()) {
                emitter.sendAndForget(event);
            }
            return event;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit STARTED event for tool %s", toolReference.getName());
            return null;
        }
    }

    /**
     * Emits a COMPLETED event for a successful tool call.
     *
     * @param eventId the event ID from the STARTED event
     * @param content the response content
     * @param duration the execution duration in milliseconds
     */
    public void emitCompletedEvent(String eventId, String content, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.completed(eventId, content, duration);
            if (emitter.hasRequests()) {
                emitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit COMPLETED event for %s", eventId);
        }
    }

    /**
     * Emits a FAILED event for a failed tool call.
     *
     * @param eventId the event ID from the STARTED event
     * @param category the error category
     * @param errorMessage the error message
     * @param duration the execution duration in milliseconds
     */
    public void emitFailedEvent(
            String eventId, ToolCallEvent.ErrorCategory category, String errorMessage, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.failed(eventId, category, errorMessage, null, duration);
            if (emitter.hasRequests()) {
                emitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit FAILED event for %s", eventId);
        }
    }

    /**
     * Categorizes exceptions into error categories for event reporting.
     *
     * @param e the exception to categorize
     * @return the error category
     */
    public ToolCallEvent.ErrorCategory categorizeException(Exception e) {
        String exceptionName = e.getClass().getName();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (exceptionName.contains("StatusRuntimeException")
                || exceptionName.contains("ServiceUnavailableException")
                || message.contains("unavailable")
                || message.contains("connection refused")
                || message.contains("connection reset")) {
            return ToolCallEvent.ErrorCategory.SERVICE_UNAVAILABLE;
        }

        if (e instanceof NullPointerException) {
            return ToolCallEvent.ErrorCategory.TOOL_DEFINITION_ERROR;
        }

        if (exceptionName.contains("IllegalArgumentException")
                || exceptionName.contains("JsonProcessingException")
                || message.contains("invalid argument")
                || message.contains("missing required")
                || message.contains("validation failed")) {
            return ToolCallEvent.ErrorCategory.INVALID_ARGUMENTS;
        }

        return ToolCallEvent.ErrorCategory.UNKNOWN;
    }
}
