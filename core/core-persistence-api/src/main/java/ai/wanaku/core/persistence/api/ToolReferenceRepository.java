package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;

public interface ToolReferenceRepository extends LabelAwareRepository<ToolReference, String> {

    List<ToolReference> findByName(String name);
}
