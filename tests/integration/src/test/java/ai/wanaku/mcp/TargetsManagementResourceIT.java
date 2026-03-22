package ai.wanaku.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TargetsManagementResourceIT extends WanakuIntegrationBase {
    private static final String SERVICE_TYPE_TOOL_INVOKER = "tool-invoker";
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = "resource-provider";
    private static final String SERVICE_TYPE_CODE_EXECUTION = "code-execution-engine";

    private final List<RegisteredTarget> registeredTargets = new ArrayList<>();

    @BeforeAll
    static void enableRestAssuredLogging() {
        io.restassured.RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void cleanupTargets() {
        List<RegisteredTarget> toCleanup = new ArrayList<>(registeredTargets);
        for (RegisteredTarget target : toCleanup) {
            deregisterTarget(target);
        }
        registeredTargets.clear();
    }

    @Test
    void listCapabilities_returnsDataList() {
        given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }

    @Test
    void capabilitiesList_allowsAnonymousAccess() {
        given().baseUri(baseUrl()).when().get("/api/v1/capabilities").then().statusCode(200);
    }

    @Test
    void registerToolService_succeedsAndIsListed() {
        String serviceName = uniqueServiceName("tool");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "tool-host", 8081, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.serviceName".formatted(target.id()), equalTo(serviceName))
                .body(
                        "data.find { it.id == '%s' }.serviceType".formatted(target.id()),
                        equalTo(SERVICE_TYPE_TOOL_INVOKER)));
    }

    @Test
    void registerResourceProvider_succeedsAndIsListed() {
        String serviceName = uniqueServiceName("resource");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "resource-host", 8082, SERVICE_TYPE_RESOURCE_PROVIDER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.serviceName".formatted(target.id()), equalTo(serviceName))
                .body(
                        "data.find { it.id == '%s' }.serviceType".formatted(target.id()),
                        equalTo(SERVICE_TYPE_RESOURCE_PROVIDER)));
    }

    @Test
    void register_returnsIdAndEchoesFields() {
        String serviceName = uniqueServiceName("echo");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "echo-host", 8090, SERVICE_TYPE_TOOL_INVOKER, "mcp", null, null, null));

        given().baseUri(baseUrl())
                .auth()
                .oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body(newTargetPayload(
                        target.id(),
                        serviceName,
                        "echo-host",
                        8090,
                        SERVICE_TYPE_TOOL_INVOKER,
                        "mcp",
                        null,
                        null,
                        null))
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(200)
                .body("data.id", notNullValue())
                .body("data.serviceName", equalTo(serviceName))
                .body("data.serviceSubType", equalTo("mcp"));
    }

    @Test
    void registerDuplicateId_updatesExistingTarget() {
        String id = "dup-" + UUID.randomUUID();
        String serviceName = uniqueServiceName("dup");

        registerTarget(
                newTargetPayload(id, serviceName, "host-a", 9001, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        registerTarget(
                newTargetPayload(id, serviceName, "host-b", 9002, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.host".formatted(id), equalTo("host-b"))
                .body("data.find { it.id == '%s' }.port".formatted(id), equalTo(9002)));
    }

    @Test
    void registerCodeExecution_preservesLanguageMetadata() {
        String serviceName = uniqueServiceName("code");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "code-host", 9100, SERVICE_TYPE_CODE_EXECUTION, "camel", "yaml", "dsl", "yaml"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.serviceSubType".formatted(target.id()), equalTo("camel"))
                .body("data.find { it.id == '%s' }.languageName".formatted(target.id()), equalTo("yaml"))
                .body("data.find { it.id == '%s' }.languageType".formatted(target.id()), equalTo("dsl"))
                .body("data.find { it.id == '%s' }.languageSubType".formatted(target.id()), equalTo("yaml")));
    }

    @Test
    void capabilitiesList_includesMultipleServiceTypes() {
        registerTarget(newTargetPayload(
                null,
                uniqueServiceName("tool-multi"),
                "multi-tool-host",
                8201,
                SERVICE_TYPE_TOOL_INVOKER,
                null,
                null,
                null,
                null));
        registerTarget(newTargetPayload(
                null,
                uniqueServiceName("resource-multi"),
                "multi-resource-host",
                8202,
                SERVICE_TYPE_RESOURCE_PROVIDER,
                null,
                null,
                null,
                null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.serviceType", hasItem(SERVICE_TYPE_TOOL_INVOKER))
                .body("data.serviceType", hasItem(SERVICE_TYPE_RESOURCE_PROVIDER)));
    }

    @Test
    void toolsState_includesRegisteredTool() {
        String serviceName = uniqueServiceName("tool-state");
        registerTarget(newTargetPayload(
                null, serviceName, "state-host", 8301, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities/tools/state")
                .then()
                .statusCode(200)
                .body("data", hasKey(serviceName))
                .body("data.'%s'.size()".formatted(serviceName), greaterThanOrEqualTo(1)));
    }

    @Test
    void resourcesState_includesRegisteredResource() {
        String serviceName = uniqueServiceName("resource-state");
        registerTarget(newTargetPayload(
                null,
                serviceName,
                "state-resource-host",
                8302,
                SERVICE_TYPE_RESOURCE_PROVIDER,
                null,
                null,
                null,
                null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities/resources/state")
                .then()
                .statusCode(200)
                .body("data", hasKey(serviceName))
                .body("data.'%s'.size()".formatted(serviceName), greaterThanOrEqualTo(1)));
    }

    @Test
    void unregister_removesTargetFromCapabilities() {
        String serviceName = uniqueServiceName("remove");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "remove-host", 8401, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }".formatted(target.id()), notNullValue()));

        deregisterTarget(target);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }".formatted(target.id()), nullValue()));
    }

    @Test
    void unregister_missingTarget_isIdempotent() {
        Map<String, Object> payload = newTargetPayload(
                "missing-" + UUID.randomUUID(),
                "missing-service",
                "missing-host",
                8501,
                SERVICE_TYPE_TOOL_INVOKER,
                null,
                null,
                null,
                null);

        given().baseUri(baseUrl())
                .auth()
                .oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(200);
    }

    @Test
    void register_thenDeregister_endToEndLifecycle() {
        String serviceName = uniqueServiceName("lifecycle");
        RegisteredTarget target = registerTarget(newTargetPayload(
                null, serviceName, "lifecycle-host", 8601, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.serviceName".formatted(target.id()), equalTo(serviceName)));

        deregisterTarget(target);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> given().baseUri(baseUrl())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }".formatted(target.id()), nullValue()));
    }

    @Test
    void register_requiresAuthentication() {
        String serviceName = uniqueServiceName("auth-register");
        Map<String, Object> payload = newTargetPayload(
                null, serviceName, "auth-host", 8701, SERVICE_TYPE_TOOL_INVOKER, null, null, null, null);

        given().baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403)));
    }

    @Test
    void unregister_requiresAuthentication() {
        Map<String, Object> payload = newTargetPayload(
                "auth-missing-" + UUID.randomUUID(),
                "auth-missing-service",
                "auth-missing-host",
                8702,
                SERVICE_TYPE_TOOL_INVOKER,
                null,
                null,
                null,
                null);

        given().baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403)));
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of();
    }

    private String baseUrl() {
        return "http://localhost:" + router.getMappedPort(8080);
    }

    private String accessToken() {
        return keycloak.getAccessToken();
    }

    private RegisteredTarget registerTarget(Map<String, Object> payload) {
        Response response = given().baseUri(baseUrl())
                .auth()
                .oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(200)
                .extract()
                .response();

        String id = response.path("data.id");
        String serviceName = response.path("data.serviceName");
        String serviceType = response.path("data.serviceType");

        RegisteredTarget target = new RegisteredTarget(id, serviceName, serviceType);
        registeredTargets.add(target);
        return target;
    }

    private void deregisterTarget(RegisteredTarget target) {
        if (target == null || target.id() == null) {
            return;
        }

        Map<String, Object> payload = newTargetPayload(
                target.id(), target.serviceName(), "cleanup-host", 8999, target.serviceType(), null, null, null, null);

        given().baseUri(baseUrl())
                .auth()
                .oauth2(accessToken())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(200);

        registeredTargets.remove(target);
    }

    private static Map<String, Object> newTargetPayload(
            String id,
            String serviceName,
            String host,
            int port,
            String serviceType,
            String serviceSubType,
            String languageName,
            String languageType,
            String languageSubType) {
        Map<String, Object> payload = new HashMap<>();
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("serviceName", serviceName);
        payload.put("host", host);
        payload.put("port", port);
        payload.put("serviceType", serviceType);
        if (serviceSubType != null) {
            payload.put("serviceSubType", serviceSubType);
        }
        if (languageName != null) {
            payload.put("languageName", languageName);
        }
        if (languageType != null) {
            payload.put("languageType", languageType);
        }
        if (languageSubType != null) {
            payload.put("languageSubType", languageSubType);
        }
        return payload;
    }

    private static String uniqueServiceName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record RegisteredTarget(String id, String serviceName, String serviceType) {}
}
