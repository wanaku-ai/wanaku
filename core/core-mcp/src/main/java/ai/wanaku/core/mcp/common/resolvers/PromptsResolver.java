package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.api.exceptions.PromptNotFoundException;
import ai.wanaku.api.types.PromptReference;

/**
 * A resolver that consumes MCP requests and resolves prompt-related operations.
 */
public interface PromptsResolver extends Resolver {

    /**
     * Given a prompt name, resolves the prompt reference.
     * @param name the name of the prompt
     * @return The requested prompt reference
     * @throws PromptNotFoundException if the prompt cannot be found
     */
    PromptReference resolve(String name) throws PromptNotFoundException;
}
