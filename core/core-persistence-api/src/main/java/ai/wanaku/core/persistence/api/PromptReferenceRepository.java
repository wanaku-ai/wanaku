package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;

public interface PromptReferenceRepository extends WanakuRepository<PromptReference, String, String> {

    List<PromptReference> findByName(String name);

    List<PromptReference> findByNameAndNamespace(String name, String namespace);
}
