package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.capabilities.sdk.api.exceptions.PromptNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.core.mcp.common.resolvers.PromptsResolver;

/**
 * No-op implementation of PromptsResolver for when prompts functionality is disabled.
 */
public class NoopPromptsResolver implements PromptsResolver {

    @Override
    public PromptReference resolve(String name) throws PromptNotFoundException {
        throw new PromptNotFoundException(name);
    }
}
