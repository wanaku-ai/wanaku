package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.Namespace;
import java.util.List;

public interface NamespaceRepository extends WanakuRepository<Namespace, String> {

    List<Namespace> findByName(String name);

    List<Namespace> findFirstAvailable(String name);
}
