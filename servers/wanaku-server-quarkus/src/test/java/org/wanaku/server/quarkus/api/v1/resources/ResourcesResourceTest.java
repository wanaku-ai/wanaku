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

package org.wanaku.server.quarkus.api.v1.resources;

import java.io.File;
import java.io.IOException;
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
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.util.support.ResourcesHelper;
import org.wanaku.server.quarkus.support.TestIndexHelper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.wanaku.core.util.support.ResourcesHelper.createResource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ResourcesResourceTest {

    public static final List<ResourceReference> RESOURCE_REFERENCES = ResourcesHelper.testFixtures();

    @BeforeAll
    static void setup() throws IOException {
        File indexFile = TestIndexHelper.createResourcesIndex();

        // Verify that the file exists and is not empty
        Assumptions.assumeTrue(indexFile.exists(), "Cannot test because the index file does not exist");
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        ResourceReference resource = createResource("/tmp/resource3.jpg", "image/jpeg", "resource3.jpg");

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when().post("/api/v1/resources/expose")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("size()", is(3),
                        "[0].name", is("resource1.jpg"),
                        "[0].type", is("image/jpeg"),
                        "[0].description", is("A sample image resource"));
    }

    @Order(3)
    @Test
    void testRemove() {
        given()
                .when().put("/api/v1/resources/remove?resource=resource3.jpg")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("size()", is(2),
                        "[0].name", is("resource1.jpg"),
                        "[0].type", is("image/jpeg"),
                        "[0].description", is("A sample image resource"));
    }
}