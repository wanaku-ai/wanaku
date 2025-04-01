package ai.wanaku.core.persistence.file;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.types.ForwardEntity;
import java.nio.file.Path;

public class FileForwardReferenceRepository  extends AbstractFileRepository<ForwardReference, ForwardEntity, String> implements ForwardReferenceRepository {

    public FileForwardReferenceRepository(
            WanakuMarshallerService wanakuMarshallerService, Path file) {
        super(wanakuMarshallerService, file);
    }

    @Override
    Class<ForwardEntity> getEntityClass() {
        return ForwardEntity.class;
    }
}
