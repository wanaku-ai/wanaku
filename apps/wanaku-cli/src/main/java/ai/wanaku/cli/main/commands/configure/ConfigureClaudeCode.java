package ai.wanaku.cli.main.commands.configure;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "claude-code", description = "Print a Claude Code command to connect to Wanaku")
public class ConfigureClaudeCode extends BaseCommand {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    @CommandLine.Option(
            names = {"--host"},
            description = "Wanaku host name",
            defaultValue = DEFAULT_HOST)
    String host = DEFAULT_HOST;

    @CommandLine.Option(
            names = {"--port"},
            description = "Wanaku port",
            defaultValue = "8080")
    int port = DEFAULT_PORT;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        if (host.contains("://") || host.contains(":")) {
            printer.printErrorMessage("--host expects a bare hostname (e.g., localhost), not a URL or host:port");
            return EXIT_ERROR;
        }
        String endpoint = "http://%s:%d/mcp/sse/".formatted(host, port);
        printer.printInfoMessage("Run this command to register Wanaku with Claude Code:");
        printer.printInfoMessage("claude mcp add wanaku --transport sse " + endpoint);
        return EXIT_OK;
    }
}
