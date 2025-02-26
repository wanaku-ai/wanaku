package ai.wanaku.core.services.util;

import java.util.Map;

public class URIHelper {

    public static String buildUri(String scheme, String path, Map<String, String> parameters) {
        StringBuilder uri = new StringBuilder(scheme);

        uri.append(path);
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
}
