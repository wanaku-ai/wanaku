package ai.wanaku.routers;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import ai.wanaku.core.mcp.common.resolvers.util.NoopForwardRegistry;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import ai.wanaku.routers.resolvers.WanakuForwardRegistry;
import picocli.CommandLine;

public class ForwardProvider {
    @Inject
    CommandLine.ParseResult parseResult;

    @Produces
    ForwardRegistry getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopForwardRegistry();
        }

        return new WanakuForwardRegistry();

    }
}
