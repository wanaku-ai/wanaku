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
        convert(entity, model);

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
        convert(model, entity);

        return entity;
    }

    private static <T extends ToolReference, V extends ToolReference> void convert(T from, V to) {
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setType(from.getType());
        to.setUri(from.getUri());
        to.setInputSchema(from.getInputSchema());
    }
}
