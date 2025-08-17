package ai.wanaku.mcp;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WanakuCamelYamlRouteToolIT extends WanakuIntegrationBase {

    private static final String YAML_FILE_NAME = "my-route.camel.yaml";

    @Test
    void camelYamlToolAdditionAndVerification() {
        // Ensure tools list is initially empty
        client.when()
                .toolsList()
                .withAssert(
                        toolsPage -> Assertions.assertThat(toolsPage.tools()).isEmpty())
                .send();

        String host = String.format("http://localhost:%d", router.getMappedPort(8080));

        // Build container-visible URI
        String fileUri = "file://" + CONTAINER_RESOURCES_FOLDER + "/" + YAML_FILE_NAME;

        // Add Camel YAML tool via CLI
        int addToolExit = executeWanakuCliCommand(
                List.of(
                        "wanaku",
                        "tools",
                        "add",
                        "-n",
                        "yaml-route-tool",
                        "--description",
                        "Description of my route",
                        "--uri",
                        fileUri,
                        "--type",
                        "camel-yaml"),
                host);

        Assertions.assertThat(addToolExit)
                .as("Failed to add Camel YAML tool via CLI")
                .isEqualTo(0);

        // Verify tool is registered
        client.when()
                .toolsList()
                .withAssert(toolsPage -> {
                    Assertions.assertThat(toolsPage.tools())
                            .as("Tool list should contain exactly one tool")
                            .hasSize(1);
                    Assertions.assertThat(toolsPage.tools().getFirst().name())
                            .as("Registered tool should have the expected name")
                            .isEqualTo("yaml-route-tool");
                })
                .send();

        // Call the tool and validate response
        client.when()
                .toolsCall("yaml-route-tool")
                .withArguments(Map.of("wanaku_body", "test-input"))
                .withAssert(toolResponse -> {
                    Assertions.assertThat(toolResponse.isError()).isFalse();
                    Assertions.assertThat(
                                    toolResponse.content().getFirst().asText().text())
                            .isEqualTo("Hello Camel from route-3104");
                })
                .send();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.YAML_ROUTE);
    }
}
