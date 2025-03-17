package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.ToolReference;

public class ToolReferenceEntity extends ToolReference implements IdEntity {
    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }
}
