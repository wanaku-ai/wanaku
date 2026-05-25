package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

public interface ResourceReferenceRepository extends LabelAwareRepository<ResourceReference, String> {

    List<ResourceReference> findByName(String name);
}
