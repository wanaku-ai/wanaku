package ai.wanaku.cli.main.commands.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPromptMessage;
import picocli.CommandLine;

@CommandLine.Command(
        name = "prompt",
        description = "Get a prompt from an MCP server",
        subcommands = {McpPromptList.class})
public class McpPrompt extends BaseCommand {

    @CommandLine.Option(
            names = {"--uri"},
            description = "MCP server endpoint URI")
    String uri;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name of the prompt to get")
    String name;

    @CommandLine.Option(
            names = {"--arg"},
            description = "Prompt argument in key=value format (repeatable)",
            split = ",")
    Map<String, String> args = new LinkedHashMap<>();

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
            Map<String, Object> promptArgs = new LinkedHashMap<>(args);
            McpGetPromptResult result = client.getPrompt(name, promptArgs);
            List<McpPromptMessage> messages = result.messages();

            if (messages.isEmpty()) {
                printer.printInfoMessage("Prompt returned no messages");
                return EXIT_OK;
            }

            for (McpPromptMessage message : messages) {
                System.out.println(message.toString());
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
        return ClientUtil.createClient(uri, authTokenOverride);
    }
}
