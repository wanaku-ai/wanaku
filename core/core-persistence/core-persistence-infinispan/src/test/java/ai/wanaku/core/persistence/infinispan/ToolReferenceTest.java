package ai.wanaku.core.persistence.infinispan;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ToolReferenceTest {

    @Inject
    ToolReferenceRepository toolReferenceRepository;

    @Test
    @Order(1)
    public void insertThree() {
        for (int i = 1; i < 4; i++) {
            ToolReference toolReference = new ToolReference();
            toolReference.setId("id" + i);
            toolReference.setName("name" + i);
            toolReference.setUri("uri" + i);
            toolReference.setDescription("description" + i);
            toolReference.setType("type" + i);

            InputSchema inputSchema = new InputSchema();
            inputSchema.setType("type" + i);
            Property property = new Property();
            property.setDescription("propertyDescription" + i);
            property.setType("propertyType" + i);
            inputSchema.setProperties(Map.of("prop" + i, property));
            inputSchema.setRequired(List.of("prop" + 1));

            toolReference.setInputSchema(inputSchema);

            toolReferenceRepository.persist(toolReference);
        }
    }

    @Test
    @Order(2)
    public void list() {
        List<ToolReference> entities = toolReferenceRepository.listAll();

        assertEquals(3, entities.size());
        final Optional<ToolReference> refOpt =
                entities.stream().filter(entity -> entity.getId().equals("id1")).findFirst();

        Assertions.assertTrue(refOpt.isPresent());
        ToolReference name1Entity = refOpt.get();
        assertEquals("name1", name1Entity.getName());
        assertEquals("type1", name1Entity.getType());
        assertNotNull(name1Entity.getInputSchema());
        assertEquals("type1", name1Entity.getInputSchema().getType());
        assertEquals("description1", name1Entity.getDescription());
    }

    @Test
    @Order(3)
    public void find() {
        ToolReference model = toolReferenceRepository.findById("id1");

        Assertions.assertNotNull(model);
    }

    @Test
    @Order(4)
    public void findNonExistent() {
        Assertions.assertNull(toolReferenceRepository.findById("nameNotExists"));
    }

    @Test
    @Order(5)
    public void delete() {
        int initialSize = toolReferenceRepository.listAll().size();

        toolReferenceRepository.deleteById("id2");

        int finalSize = toolReferenceRepository.listAll().size();

        Assertions.assertTrue(finalSize < initialSize);
    }
}
