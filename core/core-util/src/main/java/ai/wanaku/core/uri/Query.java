package ai.wanaku.core.uri;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Query {
    public static final Query EMPTY = new Query(Map.of());

    public final SortedMap<String, Object> parameters;

    public Query(Map<String, Object> parameters) {
        this.parameters = new TreeMap<>(parameters);
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            this.parameters.put(entry.getKey(), URLEncoder.encode((String) entry.getValue(), StandardCharsets.UTF_8));
        }
    }

    public String build() {
        return URIHelper.buildQuery(parameters);
    }
}
