package ai.wanaku.backend.resolvers;

import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WanakuForwardRegistry implements ForwardRegistry {
    private final Map<NameNamespacePair, ForwardResolver> resolvers = new ConcurrentHashMap<>();

    @Override
    public ForwardResolver newResolverForService(NameNamespacePair namespacePair, ForwardReference forwardReference) {
        return new WanakuForwardResolver(namespacePair, forwardReference);
    }

    @Override
    public ForwardResolver getResolver(NameNamespacePair service) {
        return resolvers.get(service);
    }

    @Override
    public void link(NameNamespacePair service, ForwardResolver resolver) {
        resolvers.put(service, resolver);
    }

    @Override
    public void unlink(NameNamespacePair service) {
        resolvers.remove(service);
    }

    @Override
    public Set<NameNamespacePair> services() {
        return resolvers.keySet();
    }
}
