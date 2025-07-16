package ai.wanaku.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.wanaku.mcp.CLIHelper.*;

@QuarkusTest
public class WanakuHttpToolIT extends WanakuIntegrationBase {

    @Test
    void toolHttpImport() {
        JsonObject response = mcpExtension.listTools();

        Assertions.assertThat(response.getJsonObject("result").getJsonArray("tools")).isEmpty();

        String host = String.format("http://localhost:%d", router.getMappedPort(8080));
        executeWanakuCliCommand(List.of("wanaku",
                "tools",
                "import",
                "https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json"), host);

        response = mcpExtension.listTools();

        Assertions.assertThat(response.getJsonObject("result").getJsonArray("tools").getJsonObject(0).getString("name"))
                .isEqualTo("free-currency-conversion-tool");

        response = mcpExtension.callTool(new JsonObject()
                .put("name", "free-currency-conversion-tool")
                .put("arguments", new JsonObject()
                        .put("toCurrency", "USD")
                        .put("fromCurrency", "EUR")));

        Assertions.assertThat(response.getJsonObject("result").getBoolean("isError"))
                .isFalse();
        Assertions.assertThat(response
                        .getJsonObject("result")
                        .getJsonArray("content")
                        .getJsonObject(0))
                .isNotNull();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.HTTP);
    }
}
