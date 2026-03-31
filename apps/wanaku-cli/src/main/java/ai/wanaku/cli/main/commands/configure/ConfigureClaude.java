package ai.wanaku.cli.main.commands.configure;

import java.net.URI;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

@CommandLine.Command(name = "claude", description = "Configure Claude Desktop to connect to Wanaku")
public class ConfigureClaude extends ConfigureMcpClientCommand {

    public ConfigureClaude() {
        super();
    }

    ConfigureClaude(Path configPathOverride) {
        super(configPathOverride);
    }

    @Override
    protected String clientDisplayName() {
        return "Claude Desktop";
    }

    @Override
    protected Path resolveConfigPath() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return resolveOverrideOrDefault(Path.of(appData, "Claude", "claude_desktop_config.json"));
            }
            return resolveOverrideOrDefault(home.resolve("AppData").resolve("Roaming").resolve("Claude")
                    .resolve("claude_desktop_config.json"));
        }

        if (osName.contains("mac")) {
            return resolveOverrideOrDefault(home.resolve("Library").resolve("Application Support")
                    .resolve("Claude").resolve("claude_desktop_config.json"));
        }

        return resolveOverrideOrDefault(home.resolve(".config").resolve("Claude").resolve("claude_desktop_config.json"));
    }

    @Override
    protected ObjectNode createServerEntry(URI endpoint) {
        ObjectNode entry = newObjectNode();
        entry.put("command", "uvx");
        if ("http".equalsIgnoreCase(transport == null ? null : transport.trim())) {
            entry.set("args", newArrayNode()
                    .add("mcp-proxy")
                    .add("--transport=streamablehttp")
                    .add(endpoint.toString()));
        } else {
            entry.set("args", newArrayNode().add("mcp-proxy").add(endpoint.toString()));
        }
        entry.set("env", newObjectNode());
        return entry;
    }
}
