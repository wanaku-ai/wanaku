package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import com.mongodb.client.MongoClient;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

public class MongoDBPersistenceConfiguration {

    @Inject
    MongoClient mongoClient;

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "mongodb")
    ResourceReferenceRepository resourceReferenceRepository() {
        return new MongoDBResourceReferenceRepository(mongoClient, new WanakuMarshallerService());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "mongodb")
    ToolReferenceRepository toolReferenceRepository() {
        return new MongoDBToolReferenceRepository(mongoClient, new WanakuMarshallerService());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "mongodb")
    ForwardReferenceRepository forwardReferenceRepository() {
        return new MongoDBForwardReferenceRepository(mongoClient, new WanakuMarshallerService());
    }
}
