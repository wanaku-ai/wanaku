package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Set;

public class NoopForwardRegistry implements ForwardRegistry {
    @Override
    public ForwardResolver forService(ForwardReference service) {
        return new NoopForwardResolver();
    }

    @Override
    public void unlink(ForwardReference service) {

    }

    @Override
    public Set<ForwardReference> services() {
        return Set.of();
    }
}
