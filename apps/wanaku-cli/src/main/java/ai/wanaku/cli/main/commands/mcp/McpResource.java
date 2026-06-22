package ai.wanaku.cli.main.commands.mcp;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResourceContents;
import picocli.CommandLine;

@CommandLine.Command(
        name = "resource",
        description = "Read a resource from an MCP server",
        subcommands = {McpResourceList.class})
public class McpResource extends BaseCommand {

    @CommandLine.Option(
            names = {"--uri"},
            description = "MCP server endpoint URI",
            required = true)
    String uri;

    @CommandLine.Option(
            names = {"--resource-uri"},
            description = "URI of the resource to read",
            required = true)
    String resourceUri;

    McpClient mcpClient;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        try (McpClient client = resolveClient()) {
            McpReadResourceResult result = client.readResource(resourceUri);
            List<McpResourceContents> contents = result.contents();

            if (contents.isEmpty()) {
                printer.printInfoMessage("Resource returned no content");
                return EXIT_OK;
            }

            for (McpResourceContents content : contents) {
                printer.println(content.toString());
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
}
