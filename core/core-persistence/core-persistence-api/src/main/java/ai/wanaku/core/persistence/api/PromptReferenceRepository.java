package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;

/**
 * Repository interface for managing {@link PromptReference} entities.
 *
 * <p>This interface extends {@link WanakuRepository} to provide specific operations
 * for PromptReference objects, with default implementations for converting
 * between model and entity representations.</p>
 */
public interface PromptReferenceRepository extends WanakuRepository<PromptReference, String> {

    /**
     * Finds all prompt references with the specified name.
     * <p>
     * Multiple prompt references may share the same name but exist in different
     * namespaces or have different configurations.
     *
     * @param name the name of the prompt references to find
     * @return a list of matching prompt references, or an empty list if none found
     */
    List<PromptReference> findByName(String name);
}
