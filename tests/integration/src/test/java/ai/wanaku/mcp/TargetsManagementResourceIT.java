/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * ...
 */

package ai.wanaku.mcp;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;


import java.util.List;


import static io.restassured.RestAssured.given;


/**
 * Negative (error-path) integration tests for the Targets Management REST API.
 * Reference: Issue #910 | Related PR: #908
 */


public class TargetsManagementResourceIT extends WanakuIntegrationBase {


    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of();
    }

    private static String validTargetJson() {
        return "{\"host\": \"localhost\", \"port\": 8080, \"service\": \"http\"}";
    }


    private static boolean isAuthEnabled() {
        return System.getProperty("auth.enabled", "false").equals("true");
    }


    // -----------------------------------------------------------------------
    // 1. MALFORMED / INVALID PAYLOAD
    // -----------------------------------------------------------------------

    @Test
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
    void testRemoveNonExistentTarget() {
        given()
                .when()
                .delete("/q/targets/remove/non-existent-host-xyz/99999")
                .then()
                .statusCode(404);
    }

    @Test
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
    void testAddTargetUnauthorized() {
        Assumptions.assumeTrue(isAuthEnabled(),
                "Skipping — auth is not enabled in this test profile");
        given()
                .contentType(ContentType.JSON)
                .body(validTargetJson())
                .when()
                .post("/q/targets/add")
                .then()
                .statusCode(401);
    }

    @Test
    void testRemoveTargetUnauthorized() {
        Assumptions.assumeTrue(isAuthEnabled(),
                "Skipping — auth is not enabled in this test profile");
        given()
                .when()
                .delete("/q/targets/remove/localhost/8080")
                .then()
                .statusCode(401);
    }
}