package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.ResourceReference;

public class ResourceReferenceEntity extends ResourceReference implements IdEntity {
    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }
}
