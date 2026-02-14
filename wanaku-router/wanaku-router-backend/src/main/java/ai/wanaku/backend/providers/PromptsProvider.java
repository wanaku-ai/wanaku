package ai.wanaku.backend.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import ai.wanaku.backend.resolvers.WanakuPromptsResolver;
import ai.wanaku.core.mcp.common.resolvers.PromptsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopPromptsResolver;
import picocli.CommandLine;

/**
 * A provider for prompts resolvers.
 */
@ApplicationScoped
public class PromptsProvider extends AbstractProvider<PromptsResolver> {

    @Inject
    CommandLine.ParseResult parseResult;

    @Produces
    @Override
    @DefaultBean
    PromptsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopPromptsResolver();
        }

        return new WanakuPromptsResolver();
    }
}
