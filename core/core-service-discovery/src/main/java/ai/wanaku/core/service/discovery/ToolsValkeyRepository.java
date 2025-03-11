package ai.wanaku.core.service.discovery;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ToolReference;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToolsValkeyRepository extends ValkeyRepository<ToolReference> {
    @Override
    protected Class<ToolReference> getCls() {
        return ToolReference.class;
    }

    @Override
    protected WanakuException getException() {
        return new ToolNotFoundException();
    }
}
