package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.management.Service;

public final class ResourceRegistry extends Registry {
    private static final ResourceRegistry INSTANCE = new ResourceRegistry();

    public synchronized static ResourceRegistry getInstance() {
        return INSTANCE;
    }

    public Service getEntryForService(String service) {
        return getForService(service);
    }
}
