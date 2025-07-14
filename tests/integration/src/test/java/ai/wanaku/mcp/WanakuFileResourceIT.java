package ai.wanaku.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
public class WanakuFileResourceIT extends WanakuIntegrationBase {

    @Test
    public void exposeFileResource() {
        JsonObject response = mcpExtension.listResources();

        Assertions.assertThat(response
                .getJsonObject("result")
                .getJsonArray("resources")).isEmpty();

        executeWanakuCliCommand(List.of("wanaku",
                "resources",
                "expose",
                "--location=" + CONTAINER_RESOURCES_FOLDER + "/test.txt",
                "--mimeType=text/plain",
                "--description=\"Sample test resource added via CLI\"",
                "--name=test-file-resource",
                "--type=file"));

        response = mcpExtension.listResources();

        Assertions.assertThat(response
                        .getJsonObject("result")
                        .getJsonArray("resources")
                        .getJsonObject(0)
                        .getString("name"))
                .isEqualTo("test-file-resource");

        String uri = response.getJsonObject("result")
                .getJsonArray("resources")
                .getJsonObject(0)
                .getString("uri");

        response = mcpExtension.readResource(uri);

        Assertions.assertThat(
                        response
                                .getJsonObject("result")
                                .getJsonArray("contents")
                                .getJsonObject(0)
                                .getString("text"))
                .contains("Wanaku!!");
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.PROVIDER_FILE);
    }
}
