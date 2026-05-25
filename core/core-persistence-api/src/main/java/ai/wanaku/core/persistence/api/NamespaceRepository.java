package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.Namespace;

public interface NamespaceRepository extends LabelAwareRepository<Namespace, String> {

    List<Namespace> findByName(String name);

    List<Namespace> findFirstAvailable(String name);
}
