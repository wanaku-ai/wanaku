package ai.wanaku.core.exchange;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Represents a parsed tool invocation request containing the URI and its body.
 */
public record ParsedToolInvokeRequest(String uri, String body) {

    /**
     * Parses the URI provided by the router
     * @param toolInvokeRequest the invocation request (containing the arguments and the request URI)
     * @return the parsed URI and its body
     */
    public static ParsedToolInvokeRequest parseRequest(ToolInvokeRequest toolInvokeRequest) {
        String uri = toolInvokeRequest.getUri();
        return parseRequest(uri, toolInvokeRequest);
    }

    /**
     * Parses a URI, ignoring the one provided by the router. This is used for services that rely on simplified
     * tools/resources URI to hide the complexity of the implementation details (i.e.: such as overly complex Camel URIs).
     * @param uri the actual URI that will be parsed. The parameters will be merged w/ the provided URI from the router.
     * @param toolInvokeRequest the invocation request (containing the arguments and the request URI)
     * @return the parsed URI and its body
     */
    public static ParsedToolInvokeRequest parseRequest(String uri, ToolInvokeRequest toolInvokeRequest) {
        String body = null;
        for (var t : toolInvokeRequest.getArgumentsMap().entrySet()) {
            if (!t.getKey().equals("_body")) {
                Object o = toolInvokeRequest.getArgumentsMap().get(t.getKey());
                String encoded = URLEncoder.encode(o.toString(), StandardCharsets.UTF_8);
                uri = uri.replace(String.format("{%s}", t.getKey()), encoded);
            } else {
                body = toolInvokeRequest.getArgumentsMap().get("_body").toString();
            }
        }

        if (body == null) {
            body = "";
        }

        return new ParsedToolInvokeRequest(uri, body);
    }
}
