package ai.wanaku.cli.main.commands.mcp;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List resources available on an MCP server")
public class McpResourceList extends BaseCommand {

    @CommandLine.Option(
            names = {"--uri"},
            description = "MCP server endpoint URI",
            required = true)
    String uri;

    McpClient mcpClient;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        try (McpClient client = resolveClient()) {
            List<dev.langchain4j.mcp.client.McpResource> resources = client.listResources();

            if (resources.isEmpty()) {
                printer.printInfoMessage("No resources found");
                return EXIT_OK;
            }

            for (dev.langchain4j.mcp.client.McpResource resource : resources) {
                System.out.println(String.format(
                        "%-30s %-40s %s",
                        nullSafe(resource.name()), nullSafe(resource.uri()), nullSafe(resource.mimeType())));
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
        return ClientUtil.createClient(uri, getAuthTokenOverride());
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
