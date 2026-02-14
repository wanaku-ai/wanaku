package ai.wanaku.backend.api.v1.management.statistics;

import jakarta.ws.rs.core.Response;

import java.io.IOException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class StatisticsResourceTest extends WanakuRouterTest {

    private static KeycloakTestClient keycloakClient;

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.deleteRecursively("target/wanaku/router");
        keycloakClient = new KeycloakTestClient();
    }

    @Test
    public void testGetStatistics() {
        final String accessToken = getAccessToken();

        given().header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/management/statistics")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", notNullValue())
                .body("data.toolsCount", is(0))
                .body("data.resourcesCount", is(0))
                .body("data.promptsCount", is(0))
                .body("data.forwardsCount", is(0))
                .body("data.dataStoresCount", is(0))
                .body("data.toolCapabilities", notNullValue())
                .body("data.toolCapabilities.total", is(0))
                .body("data.toolCapabilities.active", is(0))
                .body("data.toolCapabilities.inactive", is(0))
                .body("data.resourceCapabilities", notNullValue())
                .body("data.resourceCapabilities.total", is(0))
                .body("data.resourceCapabilities.active", is(0))
                .body("data.resourceCapabilities.inactive", is(0));
    }
}
