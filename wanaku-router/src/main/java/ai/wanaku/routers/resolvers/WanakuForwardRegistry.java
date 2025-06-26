package ai.wanaku.routers.resolvers;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WanakuForwardRegistry implements ForwardRegistry {
    private final Map<ForwardReference, ForwardResolver> resolvers = new ConcurrentHashMap<>();

    @Override
    public ForwardResolver newResolverForService(ForwardReference service) {
        return new WanakuForwardResolver(service);
    }

    @Override
    public ForwardResolver getResolver(ForwardReference service) {
        return resolvers.get(service);
    }

    @Override
    public void link(ForwardReference service, ForwardResolver resolver) {
        resolvers.put(service, resolver);
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
