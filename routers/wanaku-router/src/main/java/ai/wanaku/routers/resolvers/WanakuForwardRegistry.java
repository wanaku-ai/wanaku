package ai.wanaku.routers.resolvers;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WanakuForwardRegistry implements ForwardRegistry {
    Map<ForwardReference, ForwardResolver> resolvers = new ConcurrentHashMap<>();

    @Override
    public ForwardResolver forService(ForwardReference service) {
        return resolvers.computeIfAbsent(service, v -> new WanakuForwardResolver(service));
    }

    @Override
    public void unlink(ForwardReference service) {
        resolvers.remove(service);
    }

    @Override
    public Set<ForwardReference> services() {
        return resolvers.keySet();
    }
}
