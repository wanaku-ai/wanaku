package ai.wanaku.core.persistence.file;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ToolReferenceTest {

    @Inject
    ToolReferenceRepository toolReferenceRepository;

    private static Path path;

    @BeforeAll
    public static void init() throws IOException {
        path = Path.of(
                ConfigProvider.getConfig().getValue("wanaku.persistence.file.base-folder", String.class),
                ConfigProvider.getConfig().getValue("wanaku.persistence.file.tool-reference", String.class));

        if (Files.exists(path)) {
            Files.write(path, "".getBytes());
        }
    }

    @Test
    @Order(1)
    public void insertThree() {
        for (int i = 1; i < 4; i++) {
            ToolReference toolReference = new ToolReference();
            toolReference.setName("name" + i);
            toolReference.setUri("uri" + i);
            toolReference.setDescription("description" + i);
            toolReference.setType("type" + i);

            ToolReference.InputSchema inputSchema = new ToolReference.InputSchema();
            inputSchema.setType("type" + i);
            ToolReference.Property property = new ToolReference.Property();
            property.setDescription("propertyDescription" + i);
            property.setType("propertyType" + i);
            inputSchema.setProperties(Map.of("prop" + i, property));
            inputSchema.setRequired(List.of("prop" + 1));

            toolReference.setInputSchema(inputSchema);


            toolReferenceRepository.persist(toolReference);
        }

        Assertions.assertTrue(Files.exists(path));
    }

    @Test
    @Order(2)
    public void list() {
        List<ToolReference> entities = toolReferenceRepository.listAll();

        Assertions.assertEquals(3, entities.size());
        Assertions.assertEquals("name1", entities.get(0).getName());
    }

    @Test
    @Order(3)
    public void find() {
        ToolReference model = toolReferenceRepository.findById("name1");

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

        toolReferenceRepository.deleteById("name2");

        int finalSize = toolReferenceRepository.listAll().size();

        Assertions.assertTrue(finalSize < initialSize);
    }
}
