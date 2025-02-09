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
import org.wanaku.core.util.IndexHelper;
import org.wanaku.core.util.support.ResourcesHelper;
import org.wanaku.server.quarkus.support.TestResourceResolver;

import static io.restassured.RestAssured.given;
import static org.wanaku.core.util.support.ResourcesHelper.createResource;

@QuarkusTest
public class ResourcesResourceTest {

    public static final List<ResourceReference> RESOURCE_REFERENCES = ResourcesHelper.testFixtures();

    @BeforeAll
    static void setup() throws IOException {
        File indexFile = new File(ResourcesHelper.RESOURCES_INDEX);
        if (!indexFile.getParentFile().exists()) {
            indexFile.getParentFile().mkdirs();
        }

        // Save the index to a file
        IndexHelper.saveResourcesIndex(indexFile, RESOURCE_REFERENCES);

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