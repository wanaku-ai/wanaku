package ai.wanaku.core.capabilities.common;

import static ai.wanaku.core.config.provider.api.ReservedConfigs.CONFIG_QUERY_PARAMETERS_PREFIX;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.uri.Parameter;
import ai.wanaku.core.uri.URIHelper;
import ai.wanaku.core.uri.URIParser;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Represents a parsed tool invocation request containing the URI and its body.
 */
public record ParsedToolInvokeRequest(String uri, String body) {

    /**
     * Parses the URI provided by the router
     * @param toolInvokeRequest the invocation request (containing the arguments and the request URI)
     * @param configResource the configuration resource to use for this tool invocation
     * @return the parsed URI and its body
     */
    public static ParsedToolInvokeRequest parseRequest(
            ToolInvokeRequest toolInvokeRequest, ConfigResource configResource) {
        String uri = toolInvokeRequest.getUri();
        return parseRequest(uri, toolInvokeRequest, configResource);
    }

    /**
     * Parses a URI, ignoring the one provided by the router. This is used for services that rely on simplified
     * tools/resources URI to hide the complexity of the implementation details (i.e.: such as overly complex Camel URIs).
     * @param uri the actual URI that will be parsed. The parameters will be merged w/ the provided URI from the router.
     * @param toolInvokeRequest the invocation request (containing the arguments and the request URI)
     * @param configResource the configuration resource to use for this tool invocation
     * @return the parsed URI and its body
     */
    @SuppressWarnings("unchecked")
    public static ParsedToolInvokeRequest parseRequest(
            String uri, ToolInvokeRequest toolInvokeRequest, ConfigResource configResource) {
        final Map<String, Object> map = toParameterMap(toolInvokeRequest);

        String parsedUri = URIParser.parse(uri, map);

        // Add additional configuration
        final Map<String, String> queryParameters = configResource.getConfigs(CONFIG_QUERY_PARAMETERS_PREFIX);
        parsedUri = URIHelper.addQueryParameters(parsedUri, queryParameters);

        String body = toolInvokeRequest.getBody();

        return new ParsedToolInvokeRequest(parsedUri, body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toParameterMap(ToolInvokeRequest toolInvokeRequest) {
        Map<String, String> argumentsMap = toolInvokeRequest.getArgumentsMap();

        if (argumentsMap == null) {
            argumentsMap = Collections.emptyMap();
        }

        Map<String, Object> map = new HashMap<>(argumentsMap);
        map.put(Parameter.KEY_NAME, new Parameter((Map) argumentsMap));
        return map;
    }

    /**
     * Parses a URI, ignoring the one provided by the router. This is used for services that rely on simplified
     * tools/resources URI to hide the complexity of the implementation details (i.e.: such as overly complex Camel URIs).
     * @param uri the actual URI that will be parsed. The parameters will be merged w/ the provided URI from the router.
     * @param toolInvokeRequest the invocation request (containing the arguments and the request URI)
     * @param queryParametersSupplier a supplier for the query parameters
     * @return the parsed URI and its body
     */
    @SuppressWarnings("unchecked")
    public static ParsedToolInvokeRequest parseRequest(
            String uri, ToolInvokeRequest toolInvokeRequest, Supplier<Map<String, String>> queryParametersSupplier) {
        final Map<String, Object> map = toParameterMap(toolInvokeRequest);

        String parsedUri = URIParser.parse(uri, map);

        // Add additional configuration
        final Map<String, String> queryParameters = queryParametersSupplier.get();
        parsedUri = URIHelper.addQueryParameters(parsedUri, queryParameters);

        String body = toolInvokeRequest.getBody();

        return new ParsedToolInvokeRequest(parsedUri, body);
    }
}
