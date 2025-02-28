package ai.wanaku.core.services.util;

import java.util.Map;

public class URIHelper {

    private static String buildFromBaseUri(Map<String, String> parameters, StringBuilder uri) {
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
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

        return uri.toString();
    }

    /**
     * Build a URI from a base one
     * @param baseUri the base uri (i.e.; ftp://user@host/)
     * @param parameters the query parameters
     * @return
     */
    public static String buildUri(String baseUri, Map<String, String> parameters) {
        StringBuilder uri = new StringBuilder(baseUri);

        return buildFromBaseUri(parameters, uri);
    }

    /**
     * Build a URI from the ground up
     * @param scheme URI scheme
     * @param path URI path
     * @param parameters query parameters as a Map
     * @return
     */
    public static String buildUri(String scheme, String path, Map<String, String> parameters) {
        StringBuilder uri = new StringBuilder(scheme);

        uri.append(path);
        return buildFromBaseUri(parameters, uri);
    }
}
