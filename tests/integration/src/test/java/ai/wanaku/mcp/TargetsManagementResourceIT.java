/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Negative (error-path) integration tests for the Targets Management REST API.
 *
 * <p>These tests verify that the API correctly rejects invalid, malformed, or
 * unauthorized requests and returns appropriate HTTP error status codes.
 *
 * <p>Reference: Issue #910. Related PR: #908.
 */
@QuarkusTest
public class TargetsManagementResourceIT extends WanakuIntegrationBase {

    private static final String REGISTER_ENDPOINT = "/api/v1/management/discovery";
    private static final String LIST_ENDPOINT = "/api/v1/capabilities";
    private static final String DEFAULT_SERVICE_TYPE = "tool-invoker";

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of();
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + router.getMappedPort(8080);
    }

    private String accessToken() {
        return keycloak.getAccessToken();
    }

    private static String validTargetJson() {
        return "{\"serviceName\": \"test-service\", \"host\": \"localhost\","
                + " \"port\": 8080, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}";
    }

    // -----------------------------------------------------------------------
    // 1. MALFORMED / INVALID PAYLOAD
    // -----------------------------------------------------------------------

    @Test
    void testRegisterTargetMalformedJson() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.TEXT)
                .body("malformed-body-not-json")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetEmptyBody() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 2. MISSING REQUIRED FIELDS
    // -----------------------------------------------------------------------

    @Test
    void testRegisterTargetMissingHost() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"port\": 8080,"
                        + " \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetMissingPort() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\","
                        + " \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetMissingServiceType() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\", \"port\": 8080}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetMissingServiceName() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"host\": \"localhost\", \"port\": 8080,"
                        + " \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 3. INVALID VALUES / EDGE CASES
    // -----------------------------------------------------------------------

    @Test
    void testRegisterTargetPortTooHigh() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\","
                        + " \"port\": 99999, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetNegativePort() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\","
                        + " \"port\": -1, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetEmptyHost() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"\","
                        + " \"port\": 8080, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetNullHost() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": null,"
                        + " \"port\": 8080, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 4. INVALID SERVICE TYPE
    // -----------------------------------------------------------------------

    @Test
    void testRegisterTargetInvalidServiceType() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\","
                        + " \"port\": 8080, \"serviceType\": \"INVALID_SERVICE_XYZ\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    @Test
    void testRegisterTargetEmptyServiceType() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"test\", \"host\": \"localhost\","
                        + " \"port\": 8080, \"serviceType\": \"\"}")
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(400);
    }

    // -----------------------------------------------------------------------
    // 5. NON-EXISTENT TARGET OPERATIONS
    // -----------------------------------------------------------------------

    @Test
    void testDeregisterNonExistentTarget() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body("{\"serviceName\": \"non-existent\", \"host\": \"non-existent-host\","
                        + " \"port\": 9999, \"serviceType\": \"" + DEFAULT_SERVICE_TYPE + "\"}")
                .when()
                .delete(REGISTER_ENDPOINT)
                .then()
                .statusCode(404);
    }

    @Test
    void testListCapabilitiesUnknownService() {
        given()
                .baseUri(baseUrl())
                .auth().oauth2(accessToken())
                .when()
                .get(LIST_ENDPOINT + "?serviceType=UNKNOWN_SERVICE_XYZ")
                .then()
                .statusCode(404);
    }

    // -----------------------------------------------------------------------
    // 6. UNAUTHORIZED ACCESS
    // -----------------------------------------------------------------------

    @Test
    void testRegisterTargetUnauthorized() {
        given()
                .baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(validTargetJson())
                .when()
                .post(REGISTER_ENDPOINT)
                .then()
                .statusCode(401);
    }

    @Test
    void testDeregisterTargetUnauthorized() {
        given()
                .baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(validTargetJson())
                .when()
                .delete(REGISTER_ENDPOINT)
                .then()
                .statusCode(401);
    }
}