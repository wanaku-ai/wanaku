package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ToolReference;
import java.util.List;

/**
 * Repository interface for managing {@link ToolReference} entities.
 * <p>
 * This interface extends {@link WanakuRepository} to provide persistence operations
 * for tool references, which represent executable capabilities that can be invoked
 * by AI agents to perform specific tasks.
 * </p>
 */
public interface ToolReferenceRepository extends WanakuRepository<ToolReference, String> {

    /**
     * Finds all tool references with the specified name.
     * <p>
     * Multiple tool references may share the same name but exist in different
     * namespaces or have different configurations.
     * </p>
     *
     * @param name the name of the tool references to find
     * @return a list of matching tool references, or an empty list if none found
     */
    List<ToolReference> findByName(String name);
}
