package ai.wanaku.core.persistence.file;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.types.ResourceReferenceEntity;

import java.nio.file.Path;

public class FileResourceReferenceRepository extends AbstractFileRepository<ResourceReference, ResourceReferenceEntity, String> implements ResourceReferenceRepository {

    public FileResourceReferenceRepository(WanakuMarshallerService wanakuMarshallerService, Path file) {
        super(wanakuMarshallerService, file);
    }

    @Override
    Class<ResourceReferenceEntity> getEntityClass() {
        return ResourceReferenceEntity.class;
    }
}
