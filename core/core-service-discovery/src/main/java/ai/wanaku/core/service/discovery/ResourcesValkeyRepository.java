package ai.wanaku.core.service.discovery;

import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ResourceReference;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResourcesValkeyRepository extends ValkeyRepository<ResourceReference> {

    @Override
    protected Class<ResourceReference> getCls() {
        return ResourceReference.class;
    }

    @Override
    protected WanakuException getException() {
        return new ResourceNotFoundException();
    }

}
