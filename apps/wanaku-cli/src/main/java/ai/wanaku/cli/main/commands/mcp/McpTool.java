package ai.wanaku.cli.main.commands.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jline.terminal.Terminal;
import io.vertx.core.json.JsonObject;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import picocli.CommandLine;

@CommandLine.Command(
        name = "tool",
        description = "Call a tool on an MCP server",
        subcommands = {McpToolList.class})
public class McpTool extends BaseCommand {

    @CommandLine.Option(
            names = {"--uri"},
            description = "MCP server endpoint URI")
    String uri;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name of the tool to call")
    String name;

    @CommandLine.Option(
            names = {"--param"},
            description = "Tool parameter in key=value format (repeatable)",
            split = ",")
    Map<String, String> params = new LinkedHashMap<>();

    McpClient mcpClient;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        if (uri == null) {
            printer.printErrorMessage("Missing required option: --uri");
            return EXIT_ERROR;
        }
        if (name == null) {
            printer.printErrorMessage("Missing required option: --name");
            return EXIT_ERROR;
        }
        try (McpClient client = resolveClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(name)
                    .arguments(serializeParams(params))
                    .build();

            ToolExecutionResult result = client.executeTool(request);
            if (result.isError()) {
                printer.printErrorMessage(result.resultText());
                return EXIT_ERROR;
            }

            System.out.println(result.resultText());
            return EXIT_OK;
        } catch (Exception e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }

    private McpClient resolveClient() {
        if (mcpClient != null) {
            return mcpClient;
        }
        return ClientUtil.createClient(uri, authTokenOverride);
    }

    static String serializeParams(Map<String, String> params) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json.toString();
    }
}
