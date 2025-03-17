package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ServiceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import com.mongodb.client.MongoClient;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@IfBuildProperty(name = "wanaku.persistence.mongodb", stringValue = "true")
@ApplicationScoped
public class MongoDBPersistenceConfiguration {

    @Inject
    MongoClient mongoClient;

    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new MongoDBResourceReferenceRepository(mongoClient, new WanakuMarshallerService());
    }

    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new MongoDBToolReferenceRepository(mongoClient, new WanakuMarshallerService());
    }

    @Produces
    ServiceRepository serviceRepository() {
        return new MongoDBServiceRepository(mongoClient, new WanakuMarshallerService());
    }
}
