package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

public interface ForwardReferenceRepository extends WanakuRepository<ForwardReference, String, String> {

    List<ForwardReference> findByName(String name);
}
