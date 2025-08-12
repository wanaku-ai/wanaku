package ai.wanaku.mcp;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WanakuHttpToolManualIT {

    @Test
    void manuTest() throws URISyntaxException {
        try {
            testInvocation();
        } finally {
            executeWanakuCliCommand(List.of("wanaku", "tools", "remove", "-n", "providence"), "http://localhost:8080");
        }
    }

    private static void testInvocation() throws URISyntaxException {
        McpAssured.McpSseTestClient client = McpAssured.newSseClient()
                .setBaseUri(new URI("http://localhost:8080/"))
                .setSsePath("mcp/sse")
                .build();

        client.connect();

        executeWanakuCliCommand(
                List.of(
                        "wanaku",
                        "tools",
                        "add",
                        "-n",
                        "providence",
                        "--description",
                        "Retrieve reading content from yesterday",
                        "--uri",
                        "http://192.168.1.12:9096/api/curated/yesterday",
                        "--type",
                        "http"),
                "http://localhost:8080");

        client.when()
                .toolsCall("providence")
                .withAssert(toolResponse -> {
                    Assertions.assertThat(toolResponse.isError()).isFalse();
                    Assertions.assertThat(toolResponse.content().get(0)).isNotNull();
                })
                .send();
    }
}
