package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.management.Service;

public final class ServiceRegistry extends Registry {
    private static final ServiceRegistry INSTANCE = new ServiceRegistry();

    public synchronized static ServiceRegistry getInstance() {
        return INSTANCE;
    }

    public Service getEntryForService(String service) {
        return getForService(service);
    }
}
