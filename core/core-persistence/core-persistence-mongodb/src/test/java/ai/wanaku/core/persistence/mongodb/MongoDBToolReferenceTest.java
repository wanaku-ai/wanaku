package ai.wanaku.core.persistence.mongodb;

import jakarta.inject.Inject;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
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
public class MongoDBToolReferenceTest {

    @Inject
    ToolReferenceRepository toolReferenceRepository;

    @Inject
    MongoClient mongoClient;

    static boolean dockerCheck() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Order(1)
    @Test
    public void persist() {
        for (int i = 1; i < 4; i++) {
            ToolReference toolReference = new ToolReference();

            InputSchema inputSchema = new InputSchema();
            inputSchema.setType("type" + i);
            Property property = new Property();
            property.setType("propertyType" + i);
            property.setDescription("propertyDescription" + i);
            inputSchema.setProperties(Map.of("prop" + i, property));
            inputSchema.setRequired(List.of("prop" + i));

            toolReference.setInputSchema(inputSchema);
            toolReference.setUri("uri" + i);
            toolReference.setType("type" + i);
            toolReference.setName("name" + i);
            toolReference.setDescription("description" + i);

            toolReferenceRepository.persist(toolReference);
        }

        Assertions.assertEquals(3, mongoClient.getDatabase("wanaku").getCollection("toolReference").countDocuments());
    }

    @Order(2)
    @Test
    public void list() {
        List<ToolReference> tools = toolReferenceRepository.listAll();
        Assertions.assertEquals(3, tools.size());

        ToolReference toolReference = tools.stream().filter(tool -> "name1".equals(tool.getName()))
                        .findFirst().get();

        Assertions.assertEquals("type1", toolReference.getType());
        Assertions.assertEquals("uri1", toolReference.getUri());
        Assertions.assertEquals("type1", toolReference.getInputSchema().getType());
        Assertions.assertEquals("propertyType1", toolReference.getInputSchema().getProperties().get("prop1").getType());
        Assertions.assertEquals("prop1", toolReference.getInputSchema().getRequired().get(0));
    }

    @Order(3)
    @Test
    public void delete() {
        Assertions.assertTrue(toolReferenceRepository.deleteById("name2"));

        List<ToolReference> tools = toolReferenceRepository.listAll();
        Assertions.assertEquals(2, tools.size());
    }

    @Order(3)
    @Test
    public void findNonExistent() {
        ToolReference toolReference = toolReferenceRepository.findById("name2");

        Assertions.assertNull(toolReference);
    }

    @Order(4)
    @Test
    public void insertDuplicateKey() {
        ToolReference toolReference = new ToolReference();
        toolReference.setName("name1");

        Assertions.assertThrows(MongoWriteException.class, () -> toolReferenceRepository.persist(toolReference));
    }
}
