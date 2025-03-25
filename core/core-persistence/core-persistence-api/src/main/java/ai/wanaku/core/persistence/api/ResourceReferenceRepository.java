package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.types.ResourceReferenceEntity;

/**
 * Repository interface for managing ResourceReference entities.
 *
 * <p>This interface extends WanakuRepository to provide specific operations
 * for ResourceReference objects, with default implementations for converting
 * between model and entity representations.</p>
 */
public interface ResourceReferenceRepository extends WanakuRepository<ResourceReference, ResourceReferenceEntity, String> {

    /**
     * Converts a ResourceReferenceEntity to its corresponding ResourceReference model.
     *
     * <p>This method maps all properties from the entity to the model.</p>
     *
     * @param entity the ResourceReferenceEntity to convert
     * @return the converted ResourceReference model
     */
    @Override
    default ResourceReference convertToModel(ResourceReferenceEntity entity) {
        ResourceReference model = new ResourceReference();
        model.setLocation(entity.getLocation());
        model.setName(entity.getName());
        model.setType(entity.getType());
        model.setDescription(entity.getDescription());
        model.setMimeType(entity.getMimeType());
        model.setType(entity.getType());

        return model;
    }

    /**
     * Converts a ResourceReference model to its corresponding ResourceReferenceEntity.
     *
     * <p>This method maps all properties from the model to the entity.</p>
     *
     * @param model the ResourceReference model to convert
     * @return the converted ResourceReferenceEntity
     */
    @Override
    default ResourceReferenceEntity convertToEntity(ResourceReference model) {
        ResourceReferenceEntity entity = new ResourceReferenceEntity();
        entity.setLocation(model.getLocation());
        entity.setName(model.getName());
        entity.setType(model.getType());
        entity.setDescription(model.getDescription());
        entity.setMimeType(model.getMimeType());
        entity.setType(model.getType());

        return entity;
    }
}
