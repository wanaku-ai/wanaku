package ai.wanaku.core.persistence.file;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
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

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResourceReferenceTest {

    @Inject
    ResourceReferenceRepository resourceReferenceRepository;

    private static Path path;

    @BeforeAll
    public static void init() throws IOException {
        path = Path.of(
                ConfigProvider.getConfig().getValue("wanaku.persistence.file.base-folder", String.class),
                ConfigProvider.getConfig().getValue("wanaku.persistence.file.resource-reference", String.class));

        if (Files.exists(path)) {
            Files.write(path, "".getBytes());
        }
    }

    @Test
    @Order(1)
    public void insertThreeFiles() {
        for (int i = 1; i < 4; i++) {
            ResourceReference resourceReference = new ResourceReference();
            resourceReference.setDescription("description" + i);
            resourceReference.setLocation("location" + i);
            resourceReference.setName("name" + i);
            resourceReference.setType("type" + i);
            resourceReference.setMimeType("mimeType" + i);

            ResourceReference.Param param1 = new ResourceReference.Param();
            param1.setName("param1" + i);
            param1.setValue("value1" + i);
            ResourceReference.Param param2 = new ResourceReference.Param();
            param2.setName("param2" + i);
            param2.setValue("value2" + i);

            resourceReference.setParams(List.of(param1, param2));
            resourceReferenceRepository.persist(resourceReference);
        }

        Assertions.assertTrue(Files.exists(path));
    }

    @Test
    @Order(2)
    public void list() {
        List<ResourceReference> entities = resourceReferenceRepository.listAll();

        Assertions.assertTrue(entities.size() == 3);
        Assertions.assertTrue(entities.get(0).getName().equals("name1"));
    }

    @Test
    @Order(3)
    public void find() {
        ResourceReference model = resourceReferenceRepository.findById("name1");

        Assertions.assertNotNull(model);
    }

    @Test
    @Order(4)
    public void findNonExistent() {
        Assertions.assertNull(resourceReferenceRepository.findById("nameNotExists"));
    }

    @Test
    @Order(5)
    public void delete() {
        int initialSize = resourceReferenceRepository.listAll().size();

        resourceReferenceRepository.deleteById("name2");

        int finalSize = resourceReferenceRepository.listAll().size();

        Assertions.assertTrue(finalSize < initialSize);
    }
}
