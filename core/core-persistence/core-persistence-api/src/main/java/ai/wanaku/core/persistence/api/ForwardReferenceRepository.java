package ai.wanaku.core.persistence.api;

import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import java.util.List;

/**
 * Repository interface for managing {@link ForwardReference} entities.
 * <p>
 * This interface extends {@link WanakuRepository} to provide persistence operations
 * for forward references, which configure the router to proxy requests to remote
 * MCP servers or external capability providers.
 */
public interface ForwardReferenceRepository extends WanakuRepository<ForwardReference, String> {
    /**
     * Finds all forward references with the specified name.
     * <p>
     * Multiple forward references may share the same name but differ in other
     * attributes such as namespace or target URI.
     *
     * @param name the name of the forward references to find
     * @return a list of matching forward references, or an empty list if none found
     */
    List<ForwardReference> findByName(String name);
}
