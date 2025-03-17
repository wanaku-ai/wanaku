package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.types.ToolReferenceEntity;
import com.mongodb.client.MongoClient;
import org.eclipse.microprofile.config.ConfigProvider;

public class MongoDBToolReferenceRepository extends AbstractMongoDBRepository<ToolReference, ToolReferenceEntity, String> implements ToolReferenceRepository {

    public MongoDBToolReferenceRepository(MongoClient mongoClient, WanakuMarshallerService wanakuMarshallerService) {
        super(mongoClient, wanakuMarshallerService);
    }

    @Override
    String databaseName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.database.name", String.class);
    }

    @Override
    String collectionName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.tool-reference.collection.name", String.class);
    }

    @Override
    Class<ToolReferenceEntity> getEntityClass() {
        return ToolReferenceEntity.class;
    }
}
