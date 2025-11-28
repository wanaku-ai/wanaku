package ai.wanaku.core.persistence.infinispan;

import static org.junit.jupiter.api.Assertions.*;

import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Test class for DataStoreRepository.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DataStoreRepositoryTest {

    @Inject
    DataStoreRepository dataStoreRepository;

    private String testId;

    @BeforeAll
    void setup() {
        ((AbstractInfinispanRepository) dataStoreRepository).deleteALl();
    }

    @Order(1)
    @Test
    void testInsert() {
        DataStore dataStore = new DataStore();
        dataStore.setName("test-data");
        dataStore.setData("Sample test data content");

        final DataStore persisted = dataStoreRepository.persist(dataStore);

        assertNotNull(persisted, "Persisted data store should not be null");
        assertNotNull(persisted.getId(), "Generated ID should not be null");
        assertEquals("test-data", persisted.getName(), "Name should match");
        assertEquals("Sample test data content", persisted.getData(), "Data should match");

        testId = persisted.getId();
    }

    @Order(2)
    @Test
    void testFindById() {
        assertNotNull(testId, "Test ID should be available from previous test");

        DataStore found = dataStoreRepository.findById(testId);

        assertNotNull(found, "Data store should be found by ID");
        assertEquals(testId, found.getId(), "ID should match");
        assertEquals("test-data", found.getName(), "Name should match");
        assertEquals("Sample test data content", found.getData(), "Data should match");
    }

    @Order(3)
    @Test
    void testFindByName() {
        List<DataStore> found = dataStoreRepository.findByName("test-data");

        assertNotNull(found, "Result list should not be null");
        assertFalse(found.isEmpty(), "Should find at least one data store");
        assertEquals(1, found.size(), "Should find exactly one data store");
        assertEquals("test-data", found.get(0).getName(), "Name should match");
    }

    @Order(4)
    @Test
    void testListAll() {
        List<DataStore> all = dataStoreRepository.listAll();

        assertNotNull(all, "Result list should not be null");
        assertFalse(all.isEmpty(), "Should have at least one data store");
        assertTrue(all.size() >= 1, "Should have at least one entry");
    }

    @Order(5)
    @Test
    void testUpdate() {
        assertNotNull(testId, "Test ID should be available");

        DataStore dataStore = dataStoreRepository.findById(testId);
        assertNotNull(dataStore, "Data store should exist");

        dataStore.setData("Updated test data content");
        boolean updated = dataStoreRepository.update(testId, dataStore);

        assertTrue(updated, "Update should return true");

        // Verify the update by fetching the record again
        DataStore updatedDataStore = dataStoreRepository.findById(testId);
        assertNotNull(updatedDataStore, "Updated data store should not be null");
        assertEquals(testId, updatedDataStore.getId(), "ID should remain the same");
        assertEquals("Updated test data content", updatedDataStore.getData(), "Data should be updated");
    }

    @Order(6)
    @Test
    void testDelete() {
        assertNotNull(testId, "Test ID should be available");

        boolean deleted = dataStoreRepository.deleteById(testId);
        assertTrue(deleted, "Delete should return true");

        DataStore found = dataStoreRepository.findById(testId);
        assertNull(found, "Data store should be deleted");
    }

    @Order(7)
    @Test
    void testFindByNameNotFound() {
        List<DataStore> found = dataStoreRepository.findByName("non-existent");

        assertNotNull(found, "Result list should not be null");
        assertTrue(found.isEmpty(), "Should not find any data stores");
    }

    @Order(8)
    @Test
    void testMultipleEntries() {
        // Insert multiple entries with same name
        DataStore ds1 = new DataStore();
        ds1.setName("duplicate-name");
        ds1.setData("First entry");
        dataStoreRepository.persist(ds1);

        DataStore ds2 = new DataStore();
        ds2.setName("duplicate-name");
        ds2.setData("Second entry");
        dataStoreRepository.persist(ds2);

        List<DataStore> found = dataStoreRepository.findByName("duplicate-name");

        assertNotNull(found, "Result list should not be null");
        assertEquals(2, found.size(), "Should find two data stores with same name");

        // Cleanup
        ((AbstractInfinispanRepository) dataStoreRepository).deleteALl();
    }
}
