package ai.wanaku.cli.main.commands.configure;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

public abstract class ConfigureMcpClientCommand extends BaseCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_TRANSPORT = "sse";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_SERVER_NAME = "wanaku";

    private final Path configPathOverride;

    @CommandLine.Option(
            names = {"--transport"},
            description = "Wanaku MCP transport to target (sse or http)",
            defaultValue = DEFAULT_TRANSPORT)
    protected String transport = DEFAULT_TRANSPORT;

    @CommandLine.Option(
            names = {"--host"},
            description = "Wanaku host name",
            defaultValue = DEFAULT_HOST)
    protected String host = DEFAULT_HOST;

    @CommandLine.Option(
            names = {"--port"},
            description = "Wanaku port",
            defaultValue = "8080")
    protected int port = DEFAULT_PORT;

    @CommandLine.Option(
            names = {"--server-name"},
            description = "MCP server name to store in the client config",
            defaultValue = DEFAULT_SERVER_NAME)
    protected String serverName = DEFAULT_SERVER_NAME;

    protected ConfigureMcpClientCommand() {
        this(null);
    }

    protected ConfigureMcpClientCommand(Path configPathOverride) {
        this.configPathOverride = configPathOverride;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            Path configPath = resolveConfigPath();
            URI endpoint = resolveWanakuEndpoint();
            ObjectNode root = readOrCreateConfig(configPath);
            ObjectNode mcpServers = ensureObjectNode(root, "mcpServers");
            mcpServers.set(serverName, createServerEntry(endpoint));
            writeConfig(configPath, root);

            printer.printSuccessMessage("Configured " + clientDisplayName() + " at " + configPath.toAbsolutePath());
            printer.printInfoMessage("Wanaku endpoint: " + endpoint);
            return EXIT_OK;
        } catch (IllegalArgumentException | IOException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }

    protected final URI resolveWanakuEndpoint() {
        String normalizedTransport = transport == null ? "" : transport.trim().toLowerCase();
        String path;
        switch (normalizedTransport) {
            case "sse":
                path = "/mcp/sse/";
                break;
            case "http":
                path = "/mcp";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported transport '" + transport + "'. Supported transports: sse, http");
        }
        return URI.create("http://" + host + ":" + port + path);
    }

    protected abstract String clientDisplayName();

    protected abstract Path resolveConfigPath();

    protected abstract ObjectNode createServerEntry(URI endpoint);

    protected final ObjectNode newObjectNode() {
        return MAPPER.createObjectNode();
    }

    protected final ArrayNode newArrayNode() {
        return MAPPER.createArrayNode();
    }

    protected final ObjectNode readOrCreateConfig(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            return newObjectNode();
        }

        String raw = Files.readString(configPath, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return newObjectNode();
        }

        JsonNode parsed = MAPPER.readTree(raw);
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalArgumentException("Existing config file must contain a JSON object: " + configPath);
        }
        return (ObjectNode) parsed;
    }

    protected final ObjectNode ensureObjectNode(ObjectNode root, String fieldName) {
        JsonNode current = root.get(fieldName);
        if (current == null) {
            return root.putObject(fieldName);
        }
        if (!current.isObject()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must be a JSON object");
        }
        return (ObjectNode) current;
    }

    protected final void writeConfig(Path configPath, ObjectNode root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), root);
    }

    protected final Path resolveOverrideOrDefault(Path defaultPath) {
        return configPathOverride != null ? configPathOverride : defaultPath;
    }
}
