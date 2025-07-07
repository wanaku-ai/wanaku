package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ResourceReference;
import java.util.List;

/**
 * Repository interface for managing ResourceReference entities.
 *
 * <p>This interface extends WanakuRepository to provide specific operations
 * for ResourceReference objects, with default implementations for converting
 * between model and entity representations.</p>
 */
public interface ResourceReferenceRepository extends WanakuRepository<ResourceReference, String> {
    List<ResourceReference> findByName(String name);
}
