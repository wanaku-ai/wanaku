package ai.wanaku.backend.core.persistence.api;

import java.util.List;
import ai.wanaku.backend.bridge.ForwardRoots;

/**
 * Repository interface for managing {@link ForwardRoots} entities.
 * <p>
 * Provides persistence operations for MCP root configurations associated
 * with forward references.
 */
public interface ForwardRootsRepository extends WanakuRepository<ForwardRoots, String> {

    /**
     * Finds forward roots by the forward reference name.
     *
     * @param forwardName the name of the forward reference
     * @return a list of matching entries, or an empty list if none found
     */
    List<ForwardRoots> findByForwardName(String forwardName);
}
