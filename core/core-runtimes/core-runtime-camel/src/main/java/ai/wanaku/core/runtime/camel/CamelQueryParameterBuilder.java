package ai.wanaku.core.runtime.camel;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ReservedConfigs;
import java.util.HashMap;
import java.util.Map;

public class CamelQueryParameterBuilder {
    private final ConfigResource configResource;

    public CamelQueryParameterBuilder(ConfigResource configResource) {
        this.configResource = configResource;
    }

    public Map<String, String> build() {
        final Map<String, String> params = new HashMap<>();
        final Map<String, String> configs = configResource.getConfigs(ReservedConfigs.CONFIG_QUERY_PARAMETERS_PREFIX);

        for (var entry : configs.entrySet()) {
            params.put(entry.getKey().substring(6), entry.getValue());
        }

        final Map<String, String> secrets = configResource.getSecrets(ReservedConfigs.CONFIG_QUERY_PARAMETERS_PREFIX);
        for (var entry : secrets.entrySet()) {
            params.put(entry.getKey().substring(6), String.format("RAW(%s)", entry.getValue()));
        }

        return params;
    }

    public static Map<String, String> build(ConfigResource configResource) {
        final CamelQueryParameterBuilder queryParameterBuilder = new CamelQueryParameterBuilder(configResource);
        return queryParameterBuilder.build();
    }
}
