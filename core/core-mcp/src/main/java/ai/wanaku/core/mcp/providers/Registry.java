package ai.wanaku.core.mcp.providers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ai.wanaku.api.types.management.Service;

class Registry {
    private final Map<String, Service> registry;

    protected Registry() {
        registry = new HashMap<>();
    }

    public void link(String service, Service target) {
        registry.put(service, target);
    }

    public void unlink(String service) {
        registry.remove(service);
    }

    public Map<String, Service> getEntries() {
        return Collections.unmodifiableMap(registry);
    }

    protected Service getForService(String service) {
        return registry.get(service);
    }
}
