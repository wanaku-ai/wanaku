package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.core.persistence.api.NamespaceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NamespaceRepositoryTest {
    private static final Logger LOG = Logger.getLogger(NamespaceRepositoryTest.class);

    @Inject
    NamespaceRepository namespaceRepository;

    private final int maxNamespaces = 10;

    @BeforeAll
    void setup() {
        ((AbstractInfinispanRepository) namespaceRepository).deleteALl();
    }

    @DisplayName("Tests that preloading records create correct records")
    @Order(1)
    @Test
    void testInsert() {
        // Preload data
        for (int i = 0; i < maxNamespaces; i++) {
            final String namespacePath = String.format("ns-%d", i);
            Namespace namespace = new Namespace();
            namespace.setPath(namespacePath);
            namespace.setName(null);

            final Namespace persisted = namespaceRepository.persist(namespace);
            Assertions.assertNotNull(persisted);
            Assertions.assertNull(persisted.getName());
            Assertions.assertNotNull(persisted.getPath());
            Assertions.assertNotNull(persisted.getId());

            LOG.infof("Created new namespace path %s", namespacePath);
        }

        Assertions.assertEquals(maxNamespaces, namespaceRepository.size());
    }

    @DisplayName("Tests that creating a new records in an empty cache create correct records")
    @Order(2)
    @Test
    void testFindFirstAvailableAfterEmpty() {
        final String namespaceName = "testFindFirstAvailableAfterEmpty";
        final List<Namespace> namespaces = namespaceRepository.findFirstAvailable(namespaceName);

        Assertions.assertEquals(1, namespaces.size());
        Namespace namespace = namespaces.getFirst();
        Assertions.assertNotNull(namespace);

        Assertions.assertNull(namespace.getName());
        namespace.setName(namespaceName);
        final Namespace persisted = namespaceRepository.persist(namespace);
        Assertions.assertNotNull(persisted);
        Assertions.assertEquals(namespaceName, persisted.getName());
    }

    @DisplayName("Tests that creating a new records in an non-empty cache create correct records")
    @Order(3)
    @Test
    void testFindFirstAvailableAfterInsert() {
        final String namespaceName = "testFindFirstAvailableAfterInsert";
        final List<Namespace> namespaces = namespaceRepository.findFirstAvailable(namespaceName);

        Assertions.assertEquals(1, namespaces.size());
        Namespace namespace = namespaces.getFirst();
        Assertions.assertNotNull(namespace);

        Assertions.assertNull(namespace.getName(), "Should have returned a namespace without name");
        namespace.setName(namespaceName);
        final Namespace persisted = namespaceRepository.persist(namespace);
        Assertions.assertNotNull(persisted);
        Assertions.assertEquals(namespaceName, persisted.getName());
    }

    @DisplayName("Tests that records can be found")
    @Order(4)
    @Test
    void testFind() {
        final String namespaceName1 = "testFindFirstAvailableAfterEmpty";
        final List<Namespace> firstAfterInsert = namespaceRepository.findByName(namespaceName1);
        Assertions.assertNotNull(firstAfterInsert);
        final Namespace first = firstAfterInsert.getFirst();
        Assertions.assertEquals(namespaceName1, first.getName());
        Assertions.assertNotNull(first.getPath());
        Assertions.assertNotNull(first.getPath());

        final String namespaceName2 = "testFindFirstAvailableAfterInsert";
        final List<Namespace> secondAfterInsert = namespaceRepository.findByName(namespaceName2);
        Assertions.assertNotNull(secondAfterInsert);
        final Namespace second = secondAfterInsert.getFirst();
        Assertions.assertEquals(namespaceName2, second.getName());
        Assertions.assertNotNull(second.getPath());
        Assertions.assertNotNull(second.getPath());
    }

    @DisplayName("Tests that does not exceed the maximum number of records")
    @Order(5)
    @Test
    void testDoesNotExceedMaxNamespaces() {
        for (int i = 2; i < maxNamespaces; i++) {
            final String namespaceName = String.format("name-%d", i);
            final List<Namespace> firstAvailable = namespaceRepository.findFirstAvailable(namespaceName);

            Namespace namespace = firstAvailable.getFirst();
            namespace.setName(namespaceName);

            final Namespace persisted = namespaceRepository.persist(namespace);
            Assertions.assertNotNull(persisted);
            Assertions.assertEquals(namespaceName, persisted.getName());
        }

        Assertions.assertEquals(10, namespaceRepository.size());
        final List<Namespace> firstAvailable = namespaceRepository.findFirstAvailable("name-11");
        Assertions.assertEquals(10, namespaceRepository.size());
        Assertions.assertEquals(0, firstAvailable.size());
    }
}
