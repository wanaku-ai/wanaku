package ai.wanaku.core.persistence.file;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.types.ToolReferenceEntity;

import java.nio.file.Path;

public class FileToolReferenceRepository extends AbstractFileRepository<ToolReference, ToolReferenceEntity, String> implements ToolReferenceRepository {

    public FileToolReferenceRepository(WanakuMarshallerService wanakuMarshallerService, Path file) {
        super(wanakuMarshallerService, file);
    }

    @Override
    Class<ToolReferenceEntity> getEntityClass() {
        return ToolReferenceEntity.class;
    }
}
