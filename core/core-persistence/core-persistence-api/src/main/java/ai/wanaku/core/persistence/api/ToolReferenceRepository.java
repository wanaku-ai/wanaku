package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.types.ToolReferenceEntity;

/**
 * Repository interface for managing ToolReference entities.
 *
 * <p>This interface extends WanakuRepository to provide specific operations
 * for ToolReference objects, with default implementations for converting
 * between model and entity representations.</p>
 */
public interface ToolReferenceRepository extends WanakuRepository<ToolReference, ToolReferenceEntity, String> {

    /**
     * Converts a ToolReferenceEntity to its corresponding ToolReference model.
     *
     * <p>This method maps all properties from the entity to the model.</p>
     *
     * @param entity the ToolReferenceEntity to convert
     * @return the converted ToolReference model
     */
    @Override
    default ToolReference convertToModel(ToolReferenceEntity entity) {
        ToolReference model = new ToolReference();
        model.setName(entity.getName());
        model.setDescription(entity.getDescription());
        model.setType(entity.getType());
        model.setUri(entity.getUri());
        model.setInputSchema(entity.getInputSchema());

        return model;
    }

    /**
     * Converts a ToolReference model to its corresponding ToolReferenceEntity.
     *
     * <p>This method maps all properties from the model to the entity, and
     * uses the model's name as the entity's ID.</p>
     *
     * @param model the ToolReference model to convert
     * @return the converted ToolReferenceEntity
     */
    @Override
    default ToolReferenceEntity convertToEntity(ToolReference model) {
        ToolReferenceEntity entity = new ToolReferenceEntity();
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setType(model.getType());
        entity.setUri(model.getUri());
        entity.setInputSchema(model.getInputSchema());
        entity.setId(model.getName());

        return entity;
    }
}
