/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.mcp;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;

/**
 * Negative (error-path) integration tests for the Targets Management REST API.
 *
 * These tests verify that the API correctly rejects invalid, malformed, or
 * unauthorized requests and returns appropriate HTTP error status codes.
 *
 * Reference: Issue #910
 * Related PR: #908
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TargetsManagementResourceIT extends WanakuIntegrationBase {

    /**
     * Required by WanakuIntegrationBase.
     * Targets management tests do not require any downstream services.
     */
    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of();
    }

    // -----------------------------------------------------------------------
    // 1. MALFORMED / INVALID PAYLOAD
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void testAddTargetMalformedJson() {
        given()
                .contentType(ContentType.TEXT)
                .body("malformed-body-not-json")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void testAddTargetEmptyBody() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 2. MISSING REQUIRED FIELDS
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    void testAddTargetMissingHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"port\": 8080, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(4)
    void testAddTargetMissingPort() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(5)
    void testAddTargetMissingService() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 3. INVALID VALUES / EDGE CASES
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    void testAddTargetPortTooHigh() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 99999, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(7)
    void testAddTargetNegativePort() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": -1, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    void testAddTargetEmptyHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"\", \"port\": 8080, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(9)
    void testAddTargetNullHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": null, \"port\": 8080, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 4. INVALID SERVICE TYPE
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    void testAddTargetInvalidServiceType() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080, \"service\": \"INVALID_SERVICE_XYZ\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(11)
    void testAddTargetEmptyServiceType() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080, \"service\": \"\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 5. NON-EXISTENT TARGET OPERATIONS
    // -----------------------------------------------------------------------

    @Test
    @Order(12)
    void testRemoveNonExistentTarget() {
        given()
                .when()
                .delete("/q/targets/remove/non-existent-host-xyz/99999")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(13)
    void testListTargetsForUnknownService() {
        given()
                .when()
                .get("/q/targets/list/UNKNOWN_SERVICE_XYZ")
                .then()
                .statusCode(404);
    }

    // -----------------------------------------------------------------------
    // 6. UNAUTHORIZED ACCESS
    // -----------------------------------------------------------------------

    @Test
    @Order(14)
    void testAddTargetUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080, \"service\": \"http\"}")
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(15)
    void testRemoveTargetUnauthorized() {
        given()
                .when()
                .delete("/q/targets/remove/localhost/8080")
                .then()
                .statusCode(401);
    }
}