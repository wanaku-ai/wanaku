package ai.wanaku.backend.resolvers;

import jakarta.inject.Inject;

import java.util.List;
import ai.wanaku.backend.core.mcp.common.resolvers.PromptsResolver;
import ai.wanaku.backend.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.PromptNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;

/**
 * Wanaku implementation of PromptsResolver.
 */
public class WanakuPromptsResolver implements PromptsResolver {

    @Inject
    PromptReferenceRepository promptReferenceRepository;

    @Override
    public PromptReference resolve(String name) throws PromptNotFoundException {
        List<PromptReference> prompts = promptReferenceRepository.findByName(name);
        if (prompts.isEmpty()) {
            throw new PromptNotFoundException(name);
        }
        return prompts.get(0);
    }
}
