package ai.wanaku.core.config.provider.api;

import java.util.Map;

public class NoopConfigStore implements ConfigStore {
    @Override
    public Map<String, String> getEntries() {
        return Map.of();
    }

    @Override
    public Map<String, String> getEntries(String prefix) {
        return Map.of();
    }

    @Override
    public String get(String name) {
        return "";
    }
}
