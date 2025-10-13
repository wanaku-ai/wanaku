package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ResourceReference;
import java.util.List;

/**
 * Repository interface for managing {@link ResourceReference} entities.
 * <p>
 * This interface extends {@link WanakuRepository} to provide persistence operations
 * for resource references, which represent data sources or content that can be
 * accessed by AI agents, such as files, databases, or external data sources.
 * </p>
 */
public interface ResourceReferenceRepository extends WanakuRepository<ResourceReference, String> {
    /**
     * Finds all resource references with the specified name.
     * <p>
     * Multiple resource references may share the same name but exist in different
     * namespaces or have different configurations.
     * </p>
     *
     * @param name the name of the resource references to find
     * @return a list of matching resource references, or an empty list if none found
     */
    List<ResourceReference> findByName(String name);
}
