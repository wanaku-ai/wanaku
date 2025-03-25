package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.types.ResourceReferenceEntity;
import com.mongodb.client.MongoClient;
import org.eclipse.microprofile.config.ConfigProvider;

public class MongoDBResourceReferenceRepository extends AbstractMongoDBRepository<ResourceReference, ResourceReferenceEntity, String> implements ResourceReferenceRepository {

    public MongoDBResourceReferenceRepository(MongoClient mongoClient, WanakuMarshallerService wanakuMarshallerService) {
        super(mongoClient, wanakuMarshallerService);
    }

    @Override
    String databaseName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.database.name", String.class);
    }

    @Override
    String collectionName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.resource-reference.collection.name", String.class);
    }

    @Override
    Class<ResourceReferenceEntity> getEntityClass() {
        return ResourceReferenceEntity.class;
    }
}
