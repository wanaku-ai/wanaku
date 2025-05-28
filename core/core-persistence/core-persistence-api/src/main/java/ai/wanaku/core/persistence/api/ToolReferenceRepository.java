package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ToolReference;

/**
 * Repository interface for managing ToolReference entities.
 *
 * <p>This interface extends WanakuRepository to provide specific operations
 * for ToolReference objects, with default implementations for converting
 * between model and entity representations.</p>
 */
public interface ToolReferenceRepository extends WanakuRepository<ToolReference, String> {

}
