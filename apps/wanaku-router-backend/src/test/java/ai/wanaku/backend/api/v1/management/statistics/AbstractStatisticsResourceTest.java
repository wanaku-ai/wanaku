package ai.wanaku.backend.api.v1.management.statistics;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuRouterTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractStatisticsResourceTest extends WanakuRouterTest {

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    protected java.util.Map<String, String> getHeaders() {
        return java.util.Map.of("Content-Type", MediaType.APPLICATION_JSON);
    }

    protected String getAccessToken() {
        return "test-token";
    }

    @Test
    public void testGetStatistics() {
        given().headers(getHeaders())
                .when()
                .get("/api/v1/management/statistics")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", notNullValue())
                .body("data.toolsCount", is(0))
                .body("data.resourcesCount", is(0))
                .body("data.promptsCount", is(0))
                .body("data.forwardsCount", is(0))
                .body("data.dataStoresCount", greaterThanOrEqualTo(0))
                .body("data.toolCapabilities", notNullValue())
                .body("data.toolCapabilities.total", is(0))
                .body("data.toolCapabilities.healthy", is(0))
                .body("data.toolCapabilities.unhealthy", is(0))
                .body("data.toolCapabilities.down", is(0))
                .body("data.toolCapabilities.pending", is(0))
                .body("data.resourceCapabilities", notNullValue())
                .body("data.resourceCapabilities.total", is(0))
                .body("data.resourceCapabilities.healthy", is(0))
                .body("data.resourceCapabilities.unhealthy", is(0))
                .body("data.resourceCapabilities.down", is(0))
                .body("data.resourceCapabilities.pending", is(0));
    }
}
