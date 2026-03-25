package ai.wanaku.backend.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.CollectionsHelper;

import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.AUTH_PREFIX;
import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.BODY;
import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.METADATA_PREFIX;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
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

    private InvokerToolExecutor() {}

    /**
     * Builds a ToolInvokeRequest from the tool reference and arguments.
     *
     * @param toolReference the tool reference containing tool metadata
     * @param toolArguments the arguments provided for the tool invocation
     * @return a fully constructed ToolInvokeRequest
     */
    static ToolInvokeRequest buildToolInvokeRequest(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments) {

        // Filter out metadata and auth args before converting to string map
        Map<String, Object> filteredArgs = filterOutReservedArgs(toolArguments.args());
        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(filteredArgs);

        // Extract metadata headers from args (with prefix stripped)
        Map<String, String> metadataHeaders = extractMetadataHeaders(toolArguments);

        // Extract auth headers from args (with prefix stripped)
        Map<String, String> authHeaders = extractAuthHeaders(toolArguments);

        // Extract tool-defined headers from schema
        Map<String, String> toolDefinedHeaders = extractHeaders(toolReference, toolArguments);

        // Merge headers: metadata first, then tool-defined, then auth (auth wins on conflict)
        Map<String, String> headers = new HashMap<>(metadataHeaders);
        headers.putAll(toolDefinedHeaders);
        headers.putAll(authHeaders);

        String body = extractBody(toolReference, toolArguments);

        return ToolInvokeRequest.newBuilder()
                .setBody(body)
                .setUri(toolReference.getUri())
                .setConfigurationUri(Objects.requireNonNullElse(toolReference.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsUri(Objects.requireNonNullElse(toolReference.getSecretsURI(), EMPTY_ARGUMENT))
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

    /**
     * Extracts metadata headers from tool arguments.
     * Arguments with the "wanaku_meta_" prefix are extracted and the prefix is stripped
     * to form the header name.
     *
     * @param toolArguments the tool arguments containing potential metadata
     * @return a map of header names (with prefix stripped) to their values
     */
    static Map<String, String> extractMetadataHeaders(ToolManager.ToolArguments toolArguments) {
        return extractPrefixedHeaders(toolArguments, METADATA_PREFIX);
    }

    /**
     * Extracts authentication headers from tool arguments.
     * Arguments with the "wanaku_auth_" prefix are extracted and the prefix is stripped
     * to form the header name. These are treated as sensitive and must not be exposed
     * to LLMs or logged without redaction.
     *
     * @param toolArguments the tool arguments containing potential auth credentials
     * @return a map of header names (with prefix stripped) to their values
     */
    static Map<String, String> extractAuthHeaders(ToolManager.ToolArguments toolArguments) {
        return extractPrefixedHeaders(toolArguments, AUTH_PREFIX);
    }

    private static Map<String, String> extractPrefixedHeaders(ToolManager.ToolArguments toolArguments, String prefix) {
        return toolArguments.args().entrySet().stream()
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
