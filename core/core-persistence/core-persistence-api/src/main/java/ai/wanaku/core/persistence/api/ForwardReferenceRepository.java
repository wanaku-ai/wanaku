package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ForwardReference;
import java.util.List;

public interface ForwardReferenceRepository extends WanakuRepository<ForwardReference, String> {
    List<ForwardReference> findByName(String name);
}
