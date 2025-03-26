package ai.wanaku.core.uri;


import java.util.Map;

/**
 * Utility class to build URIs
 */
public class URIHelper {

    private URIHelper() {}

    private static String buildFromBaseUri(StringBuilder uri, Map<String, ?> parameters) {
        buildQuery(uri, parameters);

        return uri.toString();
    }

    private static void buildQuery(StringBuilder uri, Map<String, ?> parameters) {
        buildQuery(uri, parameters, true);
    }

    private static void buildQuery(StringBuilder uri, Map<String, ?> parameters, boolean first) {
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            if (first) {
                uri.append('?');
                first = false;
            } else {
                uri.append('&');
            }
            uri.append(entry.getKey());
            uri.append('=');
            uri.append(entry.getValue());
        }
    }

    /**
     * Builds only the query part of a URI
     * @param parameters the parameters to build
     * @return the query part of the URI
     */
    public static String buildQuery(Map<String, ?> parameters) {
        StringBuilder uri = new StringBuilder();

        return buildFromBaseUri(uri, parameters);
    }

    /**
     * Build a URI from a base one
     * @param baseUri the base uri (i.e.; ftp://user@host/)
     * @param parameters the query parameters
     * @return
     */
    public static String buildUri(String baseUri, Map<String, ?> parameters) {
        StringBuilder uri = new StringBuilder(baseUri);

        return buildFromBaseUri(uri, parameters);
    }

    /**
     * Build a URI from the ground up
     * @param scheme URI scheme
     * @param path URI path
     * @param parameters query parameters as a Map
     * @return
     */
    public static String buildUri(String scheme, String path, Map<String, ?> parameters) {
        StringBuilder uri = new StringBuilder(scheme);

        uri.append(path);
        return buildFromBaseUri(uri, parameters);
    }

    public static String addQueryParameters(String uri, Map<String, ?> parameters) {
        boolean first = true;
        if (uri.contains("?")) {
            first = false;
        }
        StringBuilder uriBuilder = new StringBuilder(uri);
        buildQuery(uriBuilder, parameters, first);
        return uriBuilder.toString();
    }
}
