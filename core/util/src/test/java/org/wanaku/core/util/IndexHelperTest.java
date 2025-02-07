package org.wanaku.core.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.wanaku.api.types.ResourceReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IndexHelperTest {

    public static final List<ResourceReference> RESOURCE_REFERENCES = Arrays.asList(
            createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg"),
            createResource("/tmp/resource2.txt", "text/plain", "resource2.txt")
    );

    @BeforeAll
    static void setup() {
        File indexFile = new File("target/test-classes/index.json");
        if (!indexFile.exists()) {
            indexFile.delete();
        }
    }

    @Order(1)
    @Test
    public void testSaveResourcesIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File("target/test-classes/index.json");

        // Save the index to a file
        IndexHelper.saveResourcesIndex(indexFile, RESOURCE_REFERENCES);

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(2)
    @Test
    public void testLoadResourcesIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File("target/test-classes/index.json");

        // Save the index to a file
        IndexHelper.loadResourcesIndex(indexFile);

        // Load the index back from the file
        List<ResourceReference> loadedResourceReferences = IndexHelper.loadResourcesIndex(indexFile);

        // Verify that the loaded resources match the original ones
        assertEquals(loadedResourceReferences.size(), loadedResourceReferences.size());
        for (int i = 0; i < loadedResourceReferences.size(); i++) {
            ResourceReference expected = RESOURCE_REFERENCES.get(i);
            ResourceReference actual = loadedResourceReferences.get(i);
            assertEquals(expected.getLocation(), actual.getLocation());
            assertEquals(expected.getType(), actual.getType());
            assertEquals(expected.getName(), actual.getName());
        }
    }

    private static ResourceReference createResource(String location, String type, String name) {
        ResourceReference resource = new ResourceReference();

        // Set mock data using getters and setters
        resource.setLocation(location);
        resource.setType(type);
        resource.setName(name);
        resource.setDescription("A sample image resource");

        // Create a list of Param objects for the resource's params
        List<ResourceReference.Param> params = new ArrayList<>();

        // Add some example param data to the list
        ResourceReference.Param param1 = new ResourceReference.Param();
        param1.setName("param1");
        param1.setValue("value1");
        params.add(param1);

        ResourceReference.Param param2 = new ResourceReference.Param();
        param2.setName("param2");
        param2.setValue("value2");
        params.add(param2);

        // Set the list of params for the resource
        resource.setParams(params);

        return resource;
    }


    @Order(3)
    @Test
    public void testLoadResourcesIndexThrowsException() {
        // Create a non-existent index file
        File indexFile = new File("non-existent-index.json");

        assertThrows(Exception.class, () -> IndexHelper.loadResourcesIndex(indexFile));
    }
}