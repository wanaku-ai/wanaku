package ai.wanaku.cli.main.commands.configure;

import java.net.URI;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

@CommandLine.Command(name = "cursor", description = "Configure Cursor to connect to Wanaku")
public class ConfigureCursor extends ConfigureMcpClientCommand {

    public ConfigureCursor() {
        super();
    }

    ConfigureCursor(Path configPathOverride) {
        super(configPathOverride);
    }

    @Override
    protected String clientDisplayName() {
        return "Cursor";
    }

    @Override
    protected Path resolveConfigPath() {
        return resolveOverrideOrDefault(Path.of(System.getProperty("user.home"), ".cursor", "mcp.json"));
    }

    @Override
    protected ObjectNode createServerEntry(URI endpoint) {
        ObjectNode entry = newObjectNode();
        entry.put("url", endpoint.toString());
        return entry;
    }
}
