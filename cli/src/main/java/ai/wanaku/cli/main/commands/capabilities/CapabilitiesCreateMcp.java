package ai.wanaku.cli.main.commands.capabilities;

import static picocli.CommandLine.Command;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.WanakuPrinter;
import jakarta.inject.Inject;
import java.io.IOException;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;

@Command(name = "mcp", description = "Create a new mcp service")
public class CapabilitiesCreateMcp extends CapabilitiesBase {
    private static final Logger LOG = Logger.getLogger(CapabilitiesCreateMcp.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        String baseCmd = config.mcp().createCmd();
        createProject(baseCmd, "ai.wanaku.mcp.servers", "wanaku-mcp-servers");
        return EXIT_OK;
    }
}
