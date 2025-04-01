package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.types.ForwardEntity;

public interface ForwardReferenceRepository extends WanakuRepository<ForwardReference, ForwardEntity, String> {

    default ForwardReference convertToModel(ForwardEntity entity) {
        ForwardReference model = new ForwardReference();

        model.setName(entity.getName());
        model.setAddress(entity.getAddress());
        return model;
    }

    default ForwardEntity convertToEntity(ForwardReference model) {
        ForwardEntity entity = new ForwardEntity();

        entity.setName(model.getName());
        entity.setAddress(model.getAddress());
        return entity;
    }
}
