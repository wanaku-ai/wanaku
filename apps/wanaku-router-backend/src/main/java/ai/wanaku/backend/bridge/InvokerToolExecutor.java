package ai.wanaku.backend.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.core.util.ReservedPropertyNames;

import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.AUTH_PREFIX;
import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.BODY;
import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.METADATA_PREFIX;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;

/**
 * Static utility methods for building tool invocation requests.
 * <p>
 * This class provides helper methods for:
 * <ul>
 *   <li>Building {@link ToolInvokeRequest} from tool references and arguments</li>
 *   <li>Extracting headers and body from tool arguments</li>
 *   <li>Filtering metadata arguments</li>
 * </ul>
 * <p>
 * This executor is used in composition with InvokerBridge to separate
 * tool execution concerns from bridge management concerns.
 */
public final class InvokerToolExecutor {
    private static final Logger LOG = Logger.getLogger(InvokerToolExecutor.class);
    private static final String EMPTY_BODY = "";
    private static final String EMPTY_ARGUMENT = "";
    private static final String EMPTY_REQUEST_ID = "";

    private InvokerToolExecutor() {}

    /**
     * Builds a ToolInvokeRequest from the tool reference and arguments.
     *
     * @param toolReference the tool reference containing tool metadata
     * @param callToolRequest the tool call request containing the arguments
     * @return a fully constructed ToolInvokeRequest
     */
    static ToolInvokeRequest buildToolInvokeRequest(
            ToolReference toolReference,
            McpSchema.CallToolRequest callToolRequest,
            String requestId,
            McpTransportContext transportContext) {

        // Validate required parameters before processing
        validateRequiredParameters(toolReference, callToolRequest.arguments());

        // Filter out metadata and auth args before converting to string map
        Map<String, Object> filteredArgs = filterOutReservedArgs(callToolRequest.arguments());
        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(filteredArgs);

        // Start with HTTP request headers from transport context (lowest priority)
        Map<String, String> headers = extractHttpHeaders(transportContext);

        // Extract metadata headers from args (with prefix stripped)
        Map<String, String> metadataHeaders = extractMetadataHeaders(callToolRequest);

        // Extract auth headers from args (with prefix stripped)
        Map<String, String> authHeaders = extractAuthHeaders(callToolRequest);

        // Extract tool-defined headers from schema
        Map<String, String> toolDefinedHeaders = extractHeaders(toolReference, callToolRequest);

        // Merge in order of priority: HTTP headers < metadata < tool-defined < auth
        headers.putAll(metadataHeaders);
        headers.putAll(toolDefinedHeaders);
        headers.putAll(authHeaders);

        String body = extractBody(toolReference, callToolRequest);

        return ToolInvokeRequest.newBuilder()
                .setBody(body)
                .setUri(toolReference.getUri())
                .setConfigurationUri(Objects.requireNonNullElse(toolReference.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsUri(Objects.requireNonNullElse(toolReference.getSecretsURI(), EMPTY_ARGUMENT))
                .putAllHeaders(headers)
                .putAllArguments(argumentsMap)
                .setRequestId(Objects.requireNonNullElse(requestId, EMPTY_REQUEST_ID))
                .build();
    }

    static Map<String, String> extractHeaders(ToolReference toolReference, McpSchema.CallToolRequest callToolRequest) {
        Map<String, Property> inputSchema = toolReference.getInputSchema().getProperties();

        // extract headers parameter
        return inputSchema.entrySet().stream()
                .filter(InvokerToolExecutor::extractProperties)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> evalValue(e, callToolRequest)));
    }

    /**
     * Extracts metadata headers from tool arguments.
     * Arguments with the "wanaku_meta_" prefix are extracted and the prefix is stripped
     * to form the header name.
     *
     * @param callToolRequest the tool call request containing potential metadata
     * @return a map of header names (with prefix stripped) to their values
     */
    static Map<String, String> extractMetadataHeaders(McpSchema.CallToolRequest callToolRequest) {
        return extractPrefixedHeaders(callToolRequest, METADATA_PREFIX);
    }

    /**
     * Extracts authentication headers from tool arguments.
     * Arguments with the "wanaku_auth_" prefix are extracted and the prefix is stripped
     * to form the header name. These are treated as sensitive and must not be exposed
     * to LLMs or logged without redaction.
     *
     * @param callToolRequest the tool call request containing potential auth credentials
     * @return a map of header names (with prefix stripped) to their values
     */
    static Map<String, String> extractAuthHeaders(McpSchema.CallToolRequest callToolRequest) {
        return extractPrefixedHeaders(callToolRequest, AUTH_PREFIX);
    }

    private static Map<String, String> extractPrefixedHeaders(
            McpSchema.CallToolRequest callToolRequest, String prefix) {
        return callToolRequest.arguments().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().startsWith(prefix))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(e -> e.getKey().substring(prefix.length()), e -> e.getValue()
                        .toString()));
    }

    /**
     * Filters out reserved arguments (metadata and auth) from the arguments map.
     * Arguments with the "wanaku_meta_" or "wanaku_auth_" prefix are excluded.
     *
     * @param args the original arguments map
     * @return a new map without reserved arguments
     */
    private static final Set<String> EXCLUDED_HTTP_HEADERS = Set.of(
            "accept-encoding",
            "host",
            "connection",
            "content-length",
            "content-type",
            "transfer-encoding",
            "upgrade",
            "via",
            "te",
            "trailer",
            "keep-alive",
            "proxy-authorization",
            "proxy-authenticate",
            "mcp-session-id",
            "accept",
            "origin",
            "referer",
            "sec-websocket-key",
            "sec-websocket-version",
            "sec-fetch-mode",
            "sec-fetch-site",
            "sec-fetch-dest",
            "cache-control");

    @SuppressWarnings("unchecked")
    static Map<String, String> extractHttpHeaders(McpTransportContext transportContext) {
        if (transportContext == null) {
            return new HashMap<>();
        }
        Object raw =
                transportContext.get(ai.wanaku.backend.mcp.transport.VertxStreamableTransportProvider.HTTP_HEADERS_KEY);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> headers = new HashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String value) {
                    if (!EXCLUDED_HTTP_HEADERS.contains(key.toLowerCase())) {
                        headers.put(key, value);
                    }
                }
            });
            return headers;
        }
        return new HashMap<>();
    }

    static Map<String, Object> filterOutReservedArgs(Map<String, Object> args) {
        return args.entrySet().stream()
                .filter(e -> e.getKey() == null
                        || (!e.getKey().startsWith(METADATA_PREFIX)
                                && !e.getKey().startsWith(AUTH_PREFIX)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean extractProperties(Map.Entry<String, Property> entry) {
        Property property = entry.getValue();
        return property != null
                && property.getTarget() != null
                && property.getScope() != null
                && property.getTarget().equals(TARGET_HEADER)
                && property.getScope().equals(ReservedPropertyNames.SCOPE_SERVICE);
    }

    private static String evalValue(Map.Entry<String, Property> entry, McpSchema.CallToolRequest callToolRequest) {
        if (entry.getValue() == null) {
            LOG.fatalf("Malformed value for key %s: null", entry.getKey());
            final Object fallback = callToolRequest.arguments().get(entry.getKey());
            requireNonNullValue(entry, fallback);
            return fallback.toString();
        }

        if (entry.getValue().getValue() == null) {
            final Object valueFromArgument = callToolRequest.arguments().get(entry.getKey());
            requireNonNullValue(entry, valueFromArgument);

            return valueFromArgument.toString();
        }

        final Object valueFromArgument = callToolRequest.arguments().get(entry.getKey());
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
            throw new NullPointerException(
                    "Malformed value for key %s: neither a default value nor an argument were provided (null)"
                            .formatted(entry.getKey()));
        }
    }

    static void validateRequiredParameters(ToolReference toolReference, Map<String, Object> args) {
        InputSchema inputSchema = toolReference.getInputSchema();
        if (inputSchema == null) {
            return;
        }

        List<String> required = inputSchema.getRequired();
        if (required == null || required.isEmpty()) {
            return;
        }

        Map<String, Object> safeArgs = args != null ? args : Map.of();

        List<String> missing = new ArrayList<>();
        for (String param : required) {
            Object value = safeArgs.get(param);
            if (value == null || (value instanceof String s && s.isBlank())) {
                missing.add(param);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required parameter(s): %s".formatted(String.join(", ", missing)));
        }
    }

    private static String extractBody(ToolReference toolReference, McpSchema.CallToolRequest callToolRequest) {
        // First, check if the tool specification defines it as having a body
        Map<String, Property> properties = toolReference.getInputSchema().getProperties();
        Property bodyProp = properties.get(BODY);
        if (bodyProp == null) {
            // If the tool does not specify a body, then return an empty string
            return EMPTY_BODY;
        }

        // If there is a body defined, then get it from the arguments from the LLM
        String body = (String) callToolRequest.arguments().get(BODY);
        // If the LLM does not provide a body, then return an empty string
        return Objects.requireNonNullElse(body, EMPTY_BODY);

        // Use the body provided by the LLM
    }
}
