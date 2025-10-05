package ai.wanaku.mcp;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusTest
@DisabledOnOs({OS.MAC, OS.WINDOWS})
public class WanakuHttpToolIT extends WanakuIntegrationBase {

    @Test
    void toolHttpImport() {
        client.when()
                .toolsList()
                .withAssert(
                        toolsPage -> Assertions.assertThat(toolsPage.tools()).isEmpty())
                .send();

        String host = String.format("http://localhost:%d", router.getMappedPort(8080));
        executeWanakuCliCommand(List.of("wanaku", "capabilities", "list"), host);

        executeWanakuCliCommand(
                List.of(
                        "wanaku",
                        "tools",
                        "import",
                        "https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json"),
                host);

        client.when()
                .toolsList()
                .withAssert(toolsPage -> Assertions.assertThat(
                                toolsPage.tools().get(0).name())
                        .isEqualTo("free-currency-conversion-tool"))
                .send();

        client.when()
                .toolsCall("free-currency-conversion-tool")
                .withArguments(Map.of("toCurrency", "USD", "fromCurrency", "EUR"))
                .withAssert(toolResponse -> {
                    Assertions.assertThat(toolResponse.isError()).isFalse();
                    Assertions.assertThat(toolResponse.content().get(0)).isNotNull();
                })
                .send();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.HTTP);
    }
}
