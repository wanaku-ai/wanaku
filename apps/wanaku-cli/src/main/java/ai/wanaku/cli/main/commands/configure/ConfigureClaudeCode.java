package ai.wanaku.cli.main.commands.configure;

import java.net.URI;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "claude-code", description = "Print a Claude Code command to connect to Wanaku")
public class ConfigureClaudeCode extends BaseCommand {

    private static final String DEFAULT_HOST = "http://localhost:8080";

    @CommandLine.Option(
            names = {"--host"},
            description = "Wanaku host URL",
            defaultValue = DEFAULT_HOST)
    String host = DEFAULT_HOST;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        try {
            printer.printInfoMessage("Run this command to register Wanaku with Claude Code:");
            printer.printInfoMessage("claude mcp add wanaku --transport sse " + sseEndpoint());
            return EXIT_OK;
        } catch (IllegalArgumentException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }

    private String sseEndpoint() {
        String normalizedHost = normalizeHost(host);
        return normalizedHost + "/mcp/sse";
    }

    private String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Wanaku host URL must not be blank");
        }

        URI uri = URI.create(value.trim());
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Wanaku host URL must include a scheme and host");
        }

        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
