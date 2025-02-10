/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.wanaku.api.types.ToolReference;
import org.wanaku.core.util.IndexHelper;
import org.wanaku.core.util.support.ToolsHelper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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

    @Order(1)
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

    @Order(2)
    @Test
    void testList() {
        given()
                .when().get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("size()", is(3),
                        "[0].name", is("Tool 1"),
                        "[0].type", is("http"),
                        "[0].description", is("This is a description of Tool 1."));
    }
}