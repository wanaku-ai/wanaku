package ai.wanaku.core.mcp;

import ai.wanaku.api.types.ToolReference;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;

@MongoEntity(collection = ToolReferenceEntity.COLLECTION_NAME)
public class ToolReferenceEntity extends ToolReference {
    public final static String COLLECTION_NAME = "toolsReference";

    @BsonId
    private String name;

    public ToolReferenceEntity() {
        super();
    }

    public ToolReferenceEntity(ToolReference toolReference) {
        setName(toolReference.getName());
        setDescription(toolReference.getDescription());
        setUri(toolReference.getUri());
        setType(toolReference.getType());
        setInputSchema(toolReference.getInputSchema());
    }

    public ToolReference asToolReference() {
        ToolReference toolReference = new ToolReference();
        toolReference.setName(this.getName());
        toolReference.setDescription(this.getDescription());
        toolReference.setUri(this.getUri());
        toolReference.setType(this.getType());
        toolReference.setInputSchema(this.getInputSchema());

        return toolReference;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
