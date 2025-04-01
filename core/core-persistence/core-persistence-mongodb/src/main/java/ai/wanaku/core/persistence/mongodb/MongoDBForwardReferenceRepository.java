package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.types.ForwardEntity;
import com.mongodb.client.MongoClient;
import org.eclipse.microprofile.config.ConfigProvider;

public class MongoDBForwardReferenceRepository extends AbstractMongoDBRepository<ForwardReference, ForwardEntity, String> implements ForwardReferenceRepository {

    public MongoDBForwardReferenceRepository(MongoClient mongoClient, WanakuMarshallerService wanakuMarshallerService) {
        super(mongoClient, wanakuMarshallerService);
    }

    @Override
    String databaseName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.database.name", String.class);
    }

    @Override
    String collectionName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.forward-reference.collection.name", String.class);
    }

    @Override
    Class<ForwardEntity> getEntityClass() {
        return ForwardEntity.class;
    }
}
