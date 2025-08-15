package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.NameNamespacePair;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Set;

public class NoopForwardRegistry implements ForwardRegistry {
    @Override
    public ForwardResolver newResolverForService(NameNamespacePair service, ForwardReference forwardReference) {
        return new NoopForwardResolver();
    }

    @Override
    public ForwardResolver getResolver(NameNamespacePair service) {
        return new NoopForwardResolver();
    }

    @Override
    public void link(NameNamespacePair service, ForwardResolver resolver) {}

    @Override
    public void unlink(NameNamespacePair service) {}

    @Override
    public Set<NameNamespacePair> services() {
        return Set.of();
    }
}
