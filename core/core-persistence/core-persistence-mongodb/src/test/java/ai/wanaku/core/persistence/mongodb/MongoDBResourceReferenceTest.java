package ai.wanaku.core.persistence.mongodb;

import java.util.List;

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
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@EnabledIf(value = "dockerCheck", disabledReason = "Docker environment is not available")
public class MongoDBResourceReferenceTest {

    @Inject
    ResourceReferenceRepository resourceReferenceRepository;

    @Inject
    MongoClient mongoClient;

    static boolean dockerCheck() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

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

        Assertions.assertEquals(3, mongoClient.getDatabase("wanaku").getCollection("resourceReference").countDocuments());
    }

    @Order(2)
    @Test
    public void list() {
        List<ResourceReference> resources = resourceReferenceRepository.listAll();

        Assertions.assertEquals(3, resources.size());
    }
}
