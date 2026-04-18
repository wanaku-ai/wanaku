package ai.wanaku.backend.api.v1.management.internal;

import java.net.URI;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuRouterTest;

import static io.restassured.RestAssured.port;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class InternalManagementToolsTest extends WanakuRouterTest {

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Test
    void internalManagementToolsAreRegisteredAndCallable() throws Exception {
        try (McpAssured.McpSseTestClient client = McpAssured.newSseClient()
                .setBaseUri(new URI("http://localhost:" + port + "/"))
                .setSsePath("wanaku-internal/mcp/sse")
                .build()
                .connect()) {
            client.when()
                    .toolsList(page -> {
                        assertThat(page.size()).isGreaterThanOrEqualTo(32);
                        page.findByName("wanaku-internal-info");
                        page.findByName("wanaku-internal-statistics");
                        page.findByName("wanaku-internal-fleet-status");
                        page.findByName("wanaku-internal-capabilities");
                        page.findByName("wanaku-internal-stale-capabilities");
                        page.findByName("wanaku-internal-stale-capabilities-cleanup");
                        page.findByName("wanaku-internal-namespaces");
                        page.findByName("wanaku-internal-namespace-get");
                        page.findByName("wanaku-internal-namespace-create");
                        page.findByName("wanaku-internal-namespace-update");
                        page.findByName("wanaku-internal-namespace-delete");
                        page.findByName("wanaku-internal-stale-namespaces");
                        page.findByName("wanaku-internal-stale-namespaces-cleanup");
                        page.findByName("wanaku-internal-tools");
                        page.findByName("wanaku-internal-tool-get");
                        page.findByName("wanaku-internal-tool-add");
                        page.findByName("wanaku-internal-tool-update");
                        page.findByName("wanaku-internal-tool-remove");
                        page.findByName("wanaku-internal-tools-state");
                        page.findByName("wanaku-internal-resources");
                        page.findByName("wanaku-internal-resource-get");
                        page.findByName("wanaku-internal-resource-add");
                        page.findByName("wanaku-internal-resource-update");
                        page.findByName("wanaku-internal-resource-remove");
                        page.findByName("wanaku-internal-resources-state");
                        page.findByName("wanaku-internal-prompts");
                        page.findByName("wanaku-internal-data-stores");
                        page.findByName("wanaku-internal-data-store-get");
                        page.findByName("wanaku-internal-data-store-add");
                        page.findByName("wanaku-internal-data-store-update");
                        page.findByName("wanaku-internal-data-store-remove-by-id");
                        page.findByName("wanaku-internal-data-store-remove-by-name");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-info", response -> {
                        assertThat(response.content()).isNotEmpty();
                        assertThat(response.content().get(0).asText().text()).contains("version");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-statistics", response -> {
                        assertThat(response.content()).isNotEmpty();
                        assertThat(response.content().get(0).asText().text()).contains("toolsCount");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-fleet-status", response -> {
                        assertThat(response.content()).isNotEmpty();
                        assertThat(response.content().get(0).asText().text()).contains("\"overall\":\"Healthy\"");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-stale-capabilities", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("[]");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-stale-capabilities-cleanup", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("0");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-stale-namespaces", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("[]");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-stale-namespaces-cleanup", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("0");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-tools-state", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("{}");
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("wanaku-internal-resources-state", response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().get(0).asText().text()).isEqualTo("{}");
                    })
                    .thenAssertResults();
        }
    }
}
