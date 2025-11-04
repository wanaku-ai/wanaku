package ai.wanaku.backend.resolvers;

import ai.wanaku.api.exceptions.PromptNotFoundException;
import ai.wanaku.api.types.PromptReference;
import ai.wanaku.core.mcp.common.resolvers.PromptsResolver;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import jakarta.inject.Inject;
import java.util.List;

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
