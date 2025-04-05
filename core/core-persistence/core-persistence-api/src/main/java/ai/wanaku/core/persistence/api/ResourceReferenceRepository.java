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
        convert(entity, model);

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
        convert(model, entity);

        return entity;
    }

    private static <T extends ResourceReference, V extends ResourceReference> void convert(T from, V to) {
        to.setLocation(from.getLocation());
        to.setName(from.getName());
        to.setType(from.getType());
        to.setDescription(from.getDescription());
        to.setMimeType(from.getMimeType());
        to.setType(from.getType());
    }
}
