package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.ToolReference;

@Deprecated
public class ToolReferenceEntity extends ToolReference implements WanakuEntity {
    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }
}
