package ai.wanaku.core.persistence.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResourceReferenceTest {

    @Inject
    ResourceReferenceRepository resourceReferenceRepository;

    @BeforeAll
    public void setup() {
        ((AbstractInfinispanRepository<?, ?>) resourceReferenceRepository).deleteALl();
    }

    @Test
    @Order(1)
    public void insertThreeFiles() {
        for (int i = 1; i < 4; i++) {
            ResourceReference resourceReference = new ResourceReference();
            resourceReference.setId("id" + i);
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
            resourceReference.setConfigurationURI("file://local");
            resourceReference.setSecretsURI("file://local");
            resourceReferenceRepository.persist(resourceReference);
        }
    }

    @Test
    @Order(2)
    public void list() {
        List<ResourceReference> entities = resourceReferenceRepository.listAll();

        assertEquals(3, entities.size());
        final Optional<ResourceReference> refOpt = entities.stream()
                .filter(entity -> entity.getName().equals("name1"))
                .findFirst();

        Assertions.assertTrue(refOpt.isPresent());
        ResourceReference name1Entity = refOpt.get();
        assertEquals("name1", name1Entity.getName());
        assertEquals("type1", name1Entity.getType());
        assertEquals("location1", name1Entity.getLocation());
        assertEquals("description1", name1Entity.getDescription());
        assertEquals("mimeType1", name1Entity.getMimeType());
    }

    @Test
    @Order(3)
    public void find() {
        ResourceReference model = resourceReferenceRepository.findById("id1");

        Assertions.assertNotNull(model);
    }

    @Test
    @Order(4)
    public void findNonExistent() {
        final ResourceReference nameNotExists = resourceReferenceRepository.findById("nameNotExists");
        Assertions.assertNull(nameNotExists);
    }

    @Test
    @Order(5)
    public void delete() {
        int initialSize = resourceReferenceRepository.listAll().size();

        resourceReferenceRepository.deleteById("id2");

        int finalSize = resourceReferenceRepository.listAll().size();

        Assertions.assertTrue(finalSize < initialSize);
    }
}
