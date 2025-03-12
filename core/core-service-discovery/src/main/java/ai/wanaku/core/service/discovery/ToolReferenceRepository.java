package ai.wanaku.core.service.discovery;

import ai.wanaku.api.types.ToolReference;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToolReferenceRepository implements PanacheRepositoryBase<ToolReference, String> {
}
