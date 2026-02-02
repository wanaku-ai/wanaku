package ai.wanaku.backend.bridge;

import static ai.wanaku.core.util.ReservedArgumentNames.BODY;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;

import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.mcp.common.ToolExecutor;
import ai.wanaku.core.util.CollectionsHelper;
import com.google.protobuf.ProtocolStringList;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.reactive.messaging.MutinyEmitter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Tool executor implementation for the InvokerBridge.
 * <p>
 * This class handles the actual execution of tool invocations by delegating
 * to remote tool services via gRPC. It is responsible for:
 * <ul>
 *   <li>Resolving the target service for a tool</li>
 *   <li>Extracting headers and body from tool arguments</li>
 *   <li>Invoking the remote tool service</li>
 *   <li>Processing and returning the response</li>
 * </ul>
 * <p>
 * This executor is used in composition with InvokerProxy to separate
 * tool execution concerns from proxy management concerns.
 */
public class InvokerToolExecutor implements ToolExecutor {
    private static final Logger LOG = Logger.getLogger(InvokerToolExecutor.class);
    private static final String EMPTY_BODY = "";
    private static final String EMPTY_ARGUMENT = "";
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE__TYPE_CODE_EXECUTION_ENGINE = ServiceType.CODE_EXECUTION_ENGINE.asValue();

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    /**
     * Creates a new InvokerToolExecutor.
     *
     * @param serviceResolver the service resolver for locating tool services
     */
    public InvokerToolExecutor(ServiceResolver serviceResolver) {
        this(serviceResolver, new GrpcTransport(), null);
    }

    /**
     * Creates a new InvokerToolExecutor with a custom transport.
     * <p>
     * This constructor is primarily intended for testing purposes, allowing
     * injection of a mock or custom transport implementation.
     *
     * @param serviceResolver the service resolver for locating tool services
     * @param transport the gRPC transport for communication
     */
    public InvokerToolExecutor(ServiceResolver serviceResolver, WanakuBridgeTransport transport) {
        this(serviceResolver, transport, null);
    }

    /**
     * Creates a new InvokerToolExecutor with a custom transport and event emitter.
     *
     * @param serviceResolver the service resolver for locating tool services
     * @param transport the gRPC transport for communication
     * @param toolCallEventEmitter the emitter for tool call events (nullable)
     */
    public InvokerToolExecutor(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            MutinyEmitter<ToolCallEvent> toolCallEventEmitter) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.toolCallEventEmitter = toolCallEventEmitter;
    }

    @Override
    public ToolResponse execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        LOG.infof(
                "Executing tool on behalf of connection %s",
                toolArguments.connection().id());

        if (toolReference instanceof ToolReference ref) {
            return executeToolReference(toolArguments, ref);
        }

        LOG.errorf(
                "Tool reference %s not supported",
                toolReference == null ? "null" : toolReference.getClass().getName());
        throw new UnsupportedOperationException("Only local tool call references should be invoked by this executor");
    }

    private ToolResponse executeToolReference(ToolManager.ToolArguments toolArguments, ToolReference toolReference) {
        ServiceTarget service = serviceResolver.resolve(toolReference.getType(), SERVICE_TYPE_TOOL_INVOKER);
        if (service == null) {
            // Code engines may also provide specialized tools to assist their work
            service = serviceResolver.resolve(toolReference.getType(), SERVICE__TYPE_CODE_EXECUTION_ENGINE);
            if (service == null) {
                return ToolResponse.error("There is no host registered for service " + toolReference.getType());
            }
        }

        // Build request to extract information for event
        ToolInvokeRequest request = buildToolInvokeRequest(toolReference, toolArguments);

        // Emit STARTED event
        ToolCallEvent startedEvent = null;
        if (toolCallEventEmitter != null) {
            startedEvent = emitStartedEvent(toolArguments, toolReference, service, request);
        }

        final Instant startTime = Instant.now();
        LOG.infof("Invoking %s on %s", toolReference.getType(), service);

        try {
            final ToolInvokeReply invokeReply = transport.invokeTool(request, service);
            long duration = Duration.between(startTime, Instant.now()).toMillis();

            // Emit COMPLETED or FAILED event based on response
            if (toolCallEventEmitter != null && startedEvent != null) {
                doEmitEvents(invokeReply, startedEvent, duration);
            }

            return processToolInvokeReply(invokeReply);
        } catch (Exception e) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMessage = composeErrorMessage(e);

            // Emit FAILED event with error categorization
            if (toolCallEventEmitter != null && startedEvent != null) {
                ToolCallEvent.ErrorCategory category = categorizeException(e);
                emitFailedEvent(startedEvent.getEventId(), category, errorMessage, duration);
            }

            LOG.errorf(
                    e,
                    "Unable to call endpoint: %s (connection: %s)",
                    errorMessage,
                    toolArguments.connection().id());
            return ToolResponse.error(errorMessage);
        }
    }

    private void doEmitEvents(ToolInvokeReply invokeReply, ToolCallEvent startedEvent, long duration) {
        if (invokeReply.getIsError()) {
            String errorMsg = invokeReply.getContentList().isEmpty()
                    ? "Tool execution error"
                    : invokeReply.getContentList().get(0);
            ToolCallEvent.ErrorCategory category = categorizeToolError(errorMsg);
            emitFailedEvent(startedEvent.getEventId(), category, errorMsg, duration);
        } else {
            String content =
                    invokeReply.getContentList().isEmpty() ? "" : String.join("\n", invokeReply.getContentList());
            emitCompletedEvent(startedEvent.getEventId(), content, duration);
        }
    }

    /**
     * Processes a ToolInvokeReply and converts it to a ToolResponse.
     *
     * @param invokeReply the reply from the remote tool invocation
     * @return a ToolResponse containing either success or error information
     */
    private ToolResponse processToolInvokeReply(ToolInvokeReply invokeReply) {
        if (invokeReply.getIsError()) {
            return ToolResponse.error(invokeReply.getContentList().get(0));
        }

        ProtocolStringList contentList = invokeReply.getContentList();
        List<TextContent> contents = new ArrayList<>(contentList.size());
        contentList.stream().map(TextContent::new).forEach(contents::add);

        return ToolResponse.success(contents);
    }

    private static String composeErrorMessage(Exception e) {
        if (e.getMessage() != null) {
            return e.getMessage();
        }

        return String.format(
                "An exception of type %s was thrown, but no error details were provided",
                e.getClass().getName());
    }

    /**
     * Builds a ToolInvokeRequest from the tool reference and arguments.
     *
     * @param toolReference the tool reference containing tool metadata
     * @param toolArguments the arguments provided for the tool invocation
     * @return a fully constructed ToolInvokeRequest
     */
    private ToolInvokeRequest buildToolInvokeRequest(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments) {

        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(toolArguments.args());
        Map<String, String> headers = extractHeaders(toolReference, toolArguments);
        String body = extractBody(toolReference, toolArguments);

        return ToolInvokeRequest.newBuilder()
                .setBody(body)
                .setUri(toolReference.getUri())
                .setConfigurationURI(Objects.requireNonNullElse(toolReference.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsURI(Objects.requireNonNullElse(toolReference.getSecretsURI(), EMPTY_ARGUMENT))
                .putAllHeaders(headers)
                .putAllArguments(argumentsMap)
                .build();
    }

    static Map<String, String> extractHeaders(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        Map<String, Property> inputSchema = toolReference.getInputSchema().getProperties();

        // extract headers parameter
        Map<String, String> headers = inputSchema.entrySet().stream()
                .filter(InvokerToolExecutor::extractProperties)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> evalValue(e, toolArguments)));
        return headers;
    }

    private static boolean extractProperties(Map.Entry<String, Property> entry) {
        Property property = entry.getValue();
        return property != null
                && property.getTarget() != null
                && property.getScope() != null
                && property.getTarget().equals(TARGET_HEADER)
                && property.getScope().equals(SCOPE_SERVICE);
    }

    private static String evalValue(Map.Entry<String, Property> entry, ToolManager.ToolArguments toolArguments) {
        if (entry.getValue() == null) {
            LOG.fatalf("Malformed value for key %s: null", entry.getKey());
            return toolArguments.args().get(entry.getKey()).toString();
        }

        if (entry.getValue().getValue() == null) {
            final Object valueFromArgument = toolArguments.args().get(entry.getKey());
            requireNonNullValue(entry, valueFromArgument);

            return valueFromArgument.toString();
        }

        final Object valueFromArgument = toolArguments.args().get(entry.getKey());
        if (valueFromArgument != null) {
            LOG.warnf(
                    "Overriding default value for configuration %s with the one provided by the tool", entry.getKey());
            return valueFromArgument.toString();
        }

        return entry.getValue().getValue();
    }

    private static void requireNonNullValue(Map.Entry<String, Property> entry, Object valueFromArgument) {
        if (valueFromArgument == null) {
            LOG.fatalf(
                    "Malformed value for key %s: neither a default value nor an argument were provided",
                    entry.getKey());
            throw new NullPointerException("Malformed value for key " + entry.getKey()
                    + ": neither a default value nor an argument were provided (null)");
        }
    }

    private static String extractBody(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        // First, check if the tool specification defines it as having a body
        Map<String, Property> properties = toolReference.getInputSchema().getProperties();
        Property bodyProp = properties.get(BODY);
        if (bodyProp == null) {
            // If the tool does not specify a body, then return an empty string
            return EMPTY_BODY;
        }

        // If there is a body defined, then get it from the arguments from the LLM
        String body = (String) toolArguments.args().get(BODY);
        if (body == null) {
            // If the LLM does not provide a body, then return an empty string
            return EMPTY_BODY;
        }

        // Use the body provided by the LLM
        return body;
    }

    /**
     * Emits a STARTED event for a tool call.
     */
    private ToolCallEvent emitStartedEvent(
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
                    request.getConfigurationURI(),
                    request.getSecretsURI());

            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
            return event;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit STARTED event for tool %s", toolReference.getName());
            return null;
        }
    }

    /**
     * Emits a COMPLETED event for a successful tool call.
     */
    private void emitCompletedEvent(String eventId, String content, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.completed(eventId, content, duration);
            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit COMPLETED event for %s", eventId);
        }
    }

    /**
     * Emits a FAILED event for a failed tool call.
     */
    private void emitFailedEvent(
            String eventId, ToolCallEvent.ErrorCategory category, String errorMessage, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.failed(eventId, category, errorMessage, null, duration);
            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit FAILED event for %s", eventId);
        }
    }

    /**
     * Categorizes exceptions into error categories.
     */
    private ToolCallEvent.ErrorCategory categorizeException(Exception e) {
        String exceptionName = e.getClass().getName();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // Check for gRPC / network exceptions
        if (exceptionName.contains("StatusRuntimeException")
                || exceptionName.contains("ServiceUnavailableException")
                || message.contains("unavailable")
                || message.contains("connection refused")
                || message.contains("connection reset")) {
            return ToolCallEvent.ErrorCategory.SERVICE_UNAVAILABLE;
        }

        // Check for null pointer exceptions (likely tool definition issues)
        if (e instanceof NullPointerException) {
            return ToolCallEvent.ErrorCategory.TOOL_DEFINITION_ERROR;
        }

        // Check for argument-related errors
        if (isArgumentRelatedException(exceptionName, message)) {
            return ToolCallEvent.ErrorCategory.INVALID_ARGUMENTS;
        }

        return ToolCallEvent.ErrorCategory.UNKNOWN;
    }

    private static boolean isArgumentRelatedException(String exceptionName, String message) {
        return exceptionName.contains("IllegalArgumentException")
                || exceptionName.contains("JsonProcessingException")
                || message.contains("invalid argument")
                || message.contains("missing required")
                || message.contains("validation failed");
    }

    /**
     * Categorizes tool execution errors based on error message content.
     */
    private ToolCallEvent.ErrorCategory categorizeToolError(String errorMessage) {
        if (errorMessage == null) {
            return ToolCallEvent.ErrorCategory.EXECUTION_ERROR;
        }

        String lowerMessage = errorMessage.toLowerCase();

        // Check for invalid argument patterns
        if (isArgumentRelatedMessage(lowerMessage)) {
            return ToolCallEvent.ErrorCategory.INVALID_ARGUMENTS;
        }

        // Default to execution error for tool-reported errors
        return ToolCallEvent.ErrorCategory.EXECUTION_ERROR;
    }

    private static boolean isArgumentRelatedMessage(String lowerMessage) {
        return lowerMessage.contains("invalid argument")
                || lowerMessage.contains("missing required")
                || lowerMessage.contains("validation failed")
                || lowerMessage.contains("invalid parameter")
                || lowerMessage.contains("bad request");
    }
}
