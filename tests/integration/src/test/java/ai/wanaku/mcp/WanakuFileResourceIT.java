package ai.wanaku.mcp;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import java.net.URISyntaxException;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusTest
@DisabledOnOs({OS.MAC, OS.WINDOWS})
public class WanakuFileResourceIT extends WanakuIntegrationBase {

    @Test
    public void exposeFileResource() throws URISyntaxException {
        client.when()
                .resourcesList()
                .withAssert(resourcesPage ->
                        Assertions.assertThat(resourcesPage.resources()).isEmpty())
                .send();

        String host = String.format("http://localhost:%d", router.getMappedPort(8080));

        executeWanakuCliCommand(
                List.of(
                        "wanaku",
                        "resources",
                        "expose",
                        "--location=" + CONTAINER_RESOURCES_FOLDER + "/test.txt",
                        "--mimeType=text/plain",
                        "--description=\"Sample test resource added via CLI\"",
                        "--name=test-file-resource",
                        "--type=file"),
                host);

        McpAssured.Snapshot snapshot = client.when()
                .resourcesList()
                .withAssert(resourcesPage -> Assertions.assertThat(
                                resourcesPage.resources().get(0).name())
                        .isEqualTo("test-file-resource"))
                .send()
                .thenAssertResults();
        JsonObject response = snapshot.responses().get(snapshot.responses().size() - 1);

        String uri = response.getJsonObject("result")
                .getJsonArray("resources")
                .getJsonObject(0)
                .getString("uri");

        client.when()
                .resourcesRead(uri)
                .withAssert(resourceResponse -> Assertions.assertThat(
                                resourceResponse.contents().get(0).asText().text())
                        .contains("Wanaku!!"))
                .send();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.PROVIDER_FILE);
    }
}
