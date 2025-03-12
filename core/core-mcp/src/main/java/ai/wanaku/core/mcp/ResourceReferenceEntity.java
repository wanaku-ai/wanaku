package ai.wanaku.core.mcp;

import ai.wanaku.api.types.ResourceReference;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;

@MongoEntity(collection = ResourceReferenceEntity.COLLECTION_NAME)
public class ResourceReferenceEntity extends ResourceReference {
    public final static String COLLECTION_NAME = "resourcesReference";

    @BsonId
    private String name;

    public ResourceReferenceEntity() {
        super();
    }

    public ResourceReferenceEntity(ResourceReference toolReference) {
        setLocation(toolReference.getLocation());
        setType(toolReference.getType());
        setMimeType(toolReference.getMimeType());
        setParams(toolReference.getParams());
        setName(toolReference.getName());
        setDescription(toolReference.getDescription());
    }

    public ResourceReference asResourceReference() {
        ResourceReference resourceReference = new ResourceReference();

        resourceReference.setLocation(getLocation());
        resourceReference.setType(getType());
        resourceReference.setMimeType(getMimeType());
        resourceReference.setParams(getParams());
        resourceReference.setName(getName());
        resourceReference.setDescription(getDescription());

        return resourceReference;
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
