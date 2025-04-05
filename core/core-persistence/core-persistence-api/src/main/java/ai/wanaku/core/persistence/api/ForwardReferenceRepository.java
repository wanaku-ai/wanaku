package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.types.ForwardEntity;

public interface ForwardReferenceRepository extends WanakuRepository<ForwardReference, ForwardEntity, String> {

    default ForwardReference convertToModel(ForwardEntity entity) {
        ForwardReference model = new ForwardReference();

        convert(entity, model);
        return model;
    }

    default ForwardEntity convertToEntity(ForwardReference model) {
        ForwardEntity entity = new ForwardEntity();

        convert(model, entity);
        return entity;
    }

    private static <T extends ForwardReference, V extends ForwardReference> void convert(T from, V to) {
        to.setName(from.getName());
        to.setAddress(from.getAddress());
    }
}
