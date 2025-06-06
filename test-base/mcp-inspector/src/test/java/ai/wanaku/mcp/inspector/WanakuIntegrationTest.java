package ai.wanaku.mcp.inspector;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@QuarkusTest
@Disabled("Wanaku must be running, with some tools and resources configurations")
public class WanakuIntegrationTest {

    @RegisterExtension
    public static ModelContextProtocolExtension mcpExtension = new ModelContextProtocolExtension();

    @Test
    void test() {
        // $ wanaku resources expose --location=/path/to/wanaku/README.md --mimeType=text/plain --description="Sample resource added via CLI" --name="test mcp via CLI" --type=file
        JsonObject response = mcpExtension.listResources();
        Assertions.assertThat(response.getJsonObject("result"))
                .isNotNull();
        Assertions.assertThat(response.getJsonObject("result").getJsonArray("resources"))
                .isNotNull();
        Assertions.assertThat(response.getJsonObject("result").getJsonArray("resources").getJsonObject(0).getString("name"))
                .isEqualTo("test mcp via CLI");

        String uri = response.getJsonObject("result")
                .getJsonArray("resources").getJsonObject(0).getString("uri");

        response = mcpExtension.readResource(uri);

        Assertions.assertThat(
                        response.getJsonObject("result").getJsonArray("contents").getJsonObject(0).getString("text"))
                .contains("# Wanaku - A MCP Router that connects everything");

        // $ wanaku tools import https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json
        response = mcpExtension.listTools();

        Assertions.assertThat(response.getJsonObject("result").getJsonArray("tools").getJsonObject(0).getString("name"))
                .isEqualTo("free-currency-conversion-tool");

        response = mcpExtension.callTool(new JsonObject()
                .put("name", "free-currency-conversion-tool")
                .put("arguments", new JsonObject()
                        .put("toCurrency", "USD")
                        .put("fromCurrency", "EUR")));

        Assertions.assertThat(response.getJsonObject("result").getBoolean("isError"))
                .isTrue();
    }
}
