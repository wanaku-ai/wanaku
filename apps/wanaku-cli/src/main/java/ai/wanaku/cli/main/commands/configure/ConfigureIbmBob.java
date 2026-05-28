package ai.wanaku.cli.main.commands.configure;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

@CommandLine.Command(name = "ibm-bob", description = "Configure IBM Bob to connect to Wanaku")
public class ConfigureIbmBob extends ConfigureMcpClientCommand {

    @CommandLine.Option(
            names = {"--bearer-token"},
            description = "Bearer token for Authorization header (omitted if not set)")
    protected String bearerToken;

    @CommandLine.Option(
            names = {"--always-allow"},
            description = "Tool names to add to alwaysAllow list",
            split = ",",
            defaultValue = "")
    protected List<String> alwaysAllow = List.of();

    public ConfigureIbmBob() {
        super();
    }

    ConfigureIbmBob(Path configPathOverride) {
        super(configPathOverride);
    }

    @Override
    protected String clientDisplayName() {
        return "IBM Bob";
    }

    @Override
    protected Path resolveConfigPath() {
        return resolveOverrideOrDefault(Path.of(System.getProperty("user.home"), ".bob", "mcp_settings.json"));
    }

    @Override
    protected ObjectNode createServerEntry(URI endpoint) {
        ObjectNode entry = newObjectNode();
        entry.put("url", endpoint.toString());

        if (bearerToken != null && !bearerToken.isBlank()) {
            ObjectNode headers = entry.putObject("headers");
            headers.put("Authorization", "Bearer " + bearerToken);
        }

        if (alwaysAllow != null && !alwaysAllow.isEmpty()) {
            entry.set("alwaysAllow", newArrayNode());
            for (String tool : alwaysAllow) {
                if (!tool.isBlank()) {
                    entry.withArray("alwaysAllow").add(tool);
                }
            }
        }

        return entry;
    }
}
