package ai.wanaku.core.uri;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Contains the definitions of parameters to be parsed/used
 */
@SuppressWarnings("unused")
public class Parameter {
    /**
     * Reserved keyword
     */
    public static final String KEY_NAME = "parameter";

    /**
     * An empty map of parameters
     */
    public static final Parameter EMPTY = new Parameter(Map.of());

    public final SortedMap<String, Object> parameters;

    public Parameter(Map<String, Object> parameters) {
        this.parameters = new TreeMap<>(parameters);
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            this.parameters.put(entry.getKey(), URLEncoder.encode((String) entry.getValue(), StandardCharsets.UTF_8));
        }
    }

    public Object value(String argument) {
        String filtered = argument.replace("{", "").replace("}", "");

        return parameters.get(filtered);
    }

    public Object valueOrElse(String argument, int defaultValue) {
        return valueOrElse(argument, String.valueOf(defaultValue));
    }

    public Object valueOrElse(String argument, String defaultValue) {
        Object ret = parameters.get(argument);
        if (ret == null) {
            return defaultValue;
        }
        return ret;
    }

    public String query() {
        return URIHelper.buildQuery(parameters);
    }

    public String query(String... parts) {
        SortedMap<String, Object> sorted = new TreeMap<>();

        for (String part : parts) {
            if (parameters.containsKey(part)) {
                sorted.put(part, parameters.get(part));
            }
        }

        return URIHelper.buildQuery(sorted);
    }
}
