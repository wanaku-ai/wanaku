package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class MongoDBResourceReferenceTest {

    @Inject
    ResourceReferenceRepository resourceReferenceRepository;

    @Inject
    MongoClient mongoClient;

    @Order(1)
    @Test
    public void persist() {
        for (int i = 1; i < 4; i++) {
            ResourceReference resourceReference = new ResourceReference();
            resourceReference.setType("type" + i);
            resourceReference.setMimeType("mimeType" + i);
            resourceReference.setLocation("location" + i);
            resourceReference.setName("name" + i);
            resourceReference.setDescription("description" + i);

            ResourceReference.Param param = new ResourceReference.Param();
            param.setName("param" + i);
            param.setValue("paramValue" + i);

            resourceReference.setParams(List.of(param));

            resourceReferenceRepository.persist(resourceReference);
        }

        Assertions.assertTrue(mongoClient.getDatabase("wanaku").getCollection("resourceReference").countDocuments() == 3);
    }

    @Order(2)
    @Test
    public void list() {
        List<ResourceReference> resources = resourceReferenceRepository.listAll();

        Assertions.assertTrue(resources.size() == 3);
    }
}
