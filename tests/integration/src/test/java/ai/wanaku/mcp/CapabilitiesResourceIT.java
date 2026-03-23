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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Negative (error-path) integration tests for the Capabilities REST API.
 *
 * These tests verify that the API correctly rejects invalid, malformed, or
 * unauthorized requests and returns appropriate HTTP error status codes.
 *
 * Reference: Issue #909
 * Related PR: #907
 */
public class CapabilitiesResourceIT extends WanakuIntegrationBase {

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of();
    }

    private static String validCapabilityJson() {
        return "{\"host\": \"localhost\", \"port\": 8080, \"service\": \"tool-invoker\"}";
    }

    private static boolean isAuthEnabled() {
        return System.getProperty("auth.enabled", "false").equals("true");
    }

    // -----------------------------------------------------------------------
    // 1. MALFORMED / INVALID PAYLOAD
    // -----------------------------------------------------------------------

    @Test
    void testRegisterCapabilityMalformedJson() {
        given()
                .contentType(ContentType.TEXT)
                .body("malformed-body-not-json")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityEmptyBody() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 2. MISSING REQUIRED FIELDS
    // -----------------------------------------------------------------------

    @Test
    void testRegisterCapabilityMissingHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"port\": 8080, \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityMissingPort() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityMissingService() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 3. INVALID VALUES / EDGE CASES
    // -----------------------------------------------------------------------

    @Test
    void testRegisterCapabilityPortTooHigh() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 99999, \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityNegativePort() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": -1, \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityEmptyHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"\", \"port\": 8080, \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityNullHost() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": null, \"port\": 8080, \"service\": \"tool-invoker\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 4. INVALID SERVICE TYPE
    // -----------------------------------------------------------------------

    @Test
    void testRegisterCapabilityInvalidServiceType() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080, \"service\": \"INVALID_SERVICE_XYZ\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterCapabilityEmptyServiceType() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080, \"service\": \"\"}")
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 5. NON-EXISTENT CAPABILITY OPERATIONS
    // -----------------------------------------------------------------------

    @Test
    void testDeregisterNonExistentCapability() {
        given()
                .when()
                .delete("/q/capabilities/deregister/non-existent-host-xyz/99999")
                .then()
                .statusCode(404);
    }

    @Test
    void testListCapabilitiesForUnknownService() {
        given()
                .when()
                .get("/q/capabilities/list/UNKNOWN_SERVICE_XYZ")
                .then()
                .statusCode(404);
    }

    // -----------------------------------------------------------------------
    // 6. UNAUTHORIZED ACCESS
    // -----------------------------------------------------------------------

    @Test
    void testRegisterCapabilityUnauthorized() {
        Assumptions.assumeTrue(isAuthEnabled(),
                "Skipping — auth is not enabled in this test profile");
        given()
                .contentType(ContentType.JSON)
                .body(validCapabilityJson())
                .when()
                .post("/q/capabilities/register")
                .then()
                .statusCode(401);
    }

    @Test
    void testDeregisterCapabilityUnauthorized() {
        Assumptions.assumeTrue(isAuthEnabled(),
                "Skipping — auth is not enabled in this test profile");
        given()
                .when()
                .delete("/q/capabilities/deregister/localhost/8080")
                .then()
                .statusCode(401);
    }
}