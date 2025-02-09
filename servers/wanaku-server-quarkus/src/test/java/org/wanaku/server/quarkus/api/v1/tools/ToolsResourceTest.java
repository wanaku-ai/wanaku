package org.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.MediaType;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wanaku.api.types.ToolReference;
import org.wanaku.core.util.IndexHelper;
import org.wanaku.core.util.support.ToolsHelper;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ToolsResourceTest {

    public static final List<ToolReference> TOOL_REFERENCES = ToolsHelper.testFixtures();

    @BeforeAll
    static void setup() throws IOException {
        File indexFile = new File(ToolsHelper.TOOLS_INDEX);
        if (!indexFile.getParentFile().exists()) {
            indexFile.getParentFile().mkdirs();
        }

        // Save the index to a file
        IndexHelper.saveToolsIndex(indexFile, TOOL_REFERENCES);

        // Verify that the file exists and is not empty
        Assumptions.assumeTrue(indexFile.exists(), "Cannot test because the index file does not exist");
    }

    @Test
    public void testExposeResourceSuccessfully() {
        ToolReference.InputSchema inputSchema1 = ToolsHelper.createInputSchema(
                "http",
                Collections.singletonMap("username", ToolsHelper.createProperty("string", "A username."))
        );

        ToolReference toolReference1 = ToolsHelper.createToolReference(
                "Test tool 1",
                "This is a description of the test tool 1.",
                "https://example.com/test/tool-1",
                inputSchema1
        );

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference1)
                .when().post("/api/v1/tools/add")
                .then()
                .statusCode(200);
    }
}