package ai.wanaku.mcp;

import ai.wanaku.mcp.inspector.ModelContextProtocolExtension;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

@QuarkusTest
public class WanakuHttpToolManualIT {


    @Test
    void manuTest() {
        try {
            testInvocation();
        } finally {
            executeWanakuCliCommand(List.of("wanaku", "tools", "remove", "-n", "providence"), "http://localhost:8080");
        }
    }

    private static void testInvocation() {
        ModelContextProtocolExtension mcpExtension = new ModelContextProtocolExtension(8080);

        executeWanakuCliCommand(List.of("wanaku",
                "tools",
                "add",
                "-n",
                "providence",
                "--description",
                "Retrieve reading content from yesterday",
                "--uri",
                "http://192.168.1.12:9096/api/curated/yesterday",
                "--type",
                "http"), "http://localhost:8080");

        JsonObject response = mcpExtension.callTool(new JsonObject()
                .put("name", "providence"));

        Assertions.assertThat(response.getJsonObject("result").getBoolean("isError"))
                .isFalse();
        Assertions.assertThat(response
                        .getJsonObject("result")
                        .getJsonArray("content")
                        .getJsonObject(0))
                .isNotNull();
    }
}
