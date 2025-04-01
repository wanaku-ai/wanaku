package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import java.util.Set;

public interface ForwardRegistry {

    ForwardResolver forService(ForwardReference service);

    void unlink(ForwardReference service);

    Set<ForwardReference> services();
}
