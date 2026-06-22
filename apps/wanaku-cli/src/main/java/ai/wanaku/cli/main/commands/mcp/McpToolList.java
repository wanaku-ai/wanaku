package ai.wanaku.cli.main.commands.mcp;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List tools available on an MCP server")
public class McpToolList extends BaseCommand {

    @CommandLine.Option(
            names = {"--uri"},
            description = "MCP server endpoint URI",
            required = true)
    String uri;

    McpClient mcpClient;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        try (McpClient client = resolveClient()) {
            List<ToolSpecification> tools = client.listTools();

            if (tools.isEmpty()) {
                printer.printInfoMessage("No tools found");
                return EXIT_OK;
            }

            for (ToolSpecification tool : tools) {
                printer.println(String.format("%-30s %s", tool.name(), nullSafe(tool.description())));
            }

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
        return ClientUtil.createClient(uri);
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
