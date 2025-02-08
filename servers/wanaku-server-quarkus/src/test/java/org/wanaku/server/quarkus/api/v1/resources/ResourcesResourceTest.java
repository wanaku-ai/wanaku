package org.wanaku.server.quarkus.api.v1.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.util.ResourcesHelper;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class ResourcesResourceTest {

    public static final List<ResourceReference> RESOURCE_REFERENCES = Arrays.asList(
            createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg"),
            createResource("/tmp/resource2.txt", "text/plain", "resource2.txt")
    );

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

    @BeforeAll
    static void setup() throws IOException {
        File indexFile = new File(TestResourceResolver.INDEX_FILE);
        if (!indexFile.getParentFile().exists()) {
            indexFile.getParentFile().mkdirs();
        }

        // Save the index to a file
        ResourcesHelper.saveIndex(indexFile, RESOURCE_REFERENCES);

        // Verify that the file exists and is not empty
        Assumptions.assumeTrue(indexFile.exists(), "Cannot test because the index file does not exist");
    }

    @Test
    public void testExposeResourceSuccessfully() {
        ResourceReference resource = createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg");

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when().post("/api/v1/resources/expose")
                .then()
                .statusCode(200);
    }
}