package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.ResourceReference;

@Deprecated
public class ResourceReferenceEntity extends ResourceReference implements WanakuEntity {
    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }
}
