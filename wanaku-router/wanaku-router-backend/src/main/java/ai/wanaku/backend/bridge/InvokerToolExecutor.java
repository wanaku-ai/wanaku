package ai.wanaku.backend.bridge;

import static ai.wanaku.core.util.ReservedArgumentNames.BODY;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;

import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
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

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;

    /**
     * Creates a new InvokerToolExecutor.
     *
     * @param serviceResolver the service resolver for locating tool services
     */
    public InvokerToolExecutor(ServiceResolver serviceResolver) {
        this(serviceResolver, new GrpcTransport());
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
        this.serviceResolver = serviceResolver;
        this.transport = transport;
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
        ServiceTarget service = serviceResolver.resolve(toolReference.getType(), ServiceType.TOOL_INVOKER);
        if (service == null) {
            return ToolResponse.error("There is no host registered for service " + toolReference.getType());
        }

        LOG.infof("Invoking %s on %s", toolReference.getType(), service);
        try {
            final ToolInvokeReply invokeReply = invokeRemotely(toolReference, toolArguments, service);
            return processToolInvokeReply(invokeReply);
        } catch (Exception e) {
            String errorMessage = composeErrorMessage(e);

            LOG.errorf(
                    e,
                    "Unable to call endpoint: %s (connection: %s)",
                    errorMessage,
                    toolArguments.connection().id());
            return ToolResponse.error(errorMessage);
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

    private ToolInvokeReply invokeRemotely(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments, ServiceTarget service) {
        ToolInvokeRequest request = buildToolInvokeRequest(toolReference, toolArguments);
        return transport.invokeTool(request, service);
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
}
