package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.ForwardReference;

@Deprecated
public class ForwardEntity extends ForwardReference implements WanakuEntity {
    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }
}
