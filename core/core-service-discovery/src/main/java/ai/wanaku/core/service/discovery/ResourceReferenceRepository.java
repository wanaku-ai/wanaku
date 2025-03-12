package ai.wanaku.core.service.discovery;

import ai.wanaku.api.types.ResourceReference;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResourceReferenceRepository implements PanacheRepositoryBase<ResourceReference, String> {
}
