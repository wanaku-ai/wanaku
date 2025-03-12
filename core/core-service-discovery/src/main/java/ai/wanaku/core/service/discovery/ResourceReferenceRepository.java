package ai.wanaku.core.service.discovery;

import ai.wanaku.core.mcp.ResourceReferenceEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResourceReferenceRepository implements PanacheMongoRepositoryBase<ResourceReferenceEntity, String> {
}
