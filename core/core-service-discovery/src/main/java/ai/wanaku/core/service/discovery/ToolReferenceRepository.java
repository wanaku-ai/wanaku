package ai.wanaku.core.service.discovery;

import ai.wanaku.core.mcp.ToolReferenceEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToolReferenceRepository implements PanacheMongoRepositoryBase<ToolReferenceEntity, String> {
}
