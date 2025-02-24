package ai.wanaku.api.types.management;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Configurations {
    public Map<String, Configuration> configurations = new HashMap<>();

    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, Configuration> configurations) {
        this.configurations = configurations;
    }

    public static Map<String, String> toStringMap(Map<String, Configuration> configurations) {
        return configurations.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
    }
}
