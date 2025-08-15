package ai.wanaku.cli.main.commands.capabilities;

import static picocli.CommandLine.Command;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.WanakuPrinter;
import jakarta.inject.Inject;
import java.io.IOException;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;

@Command(name = "tool", description = "Create a new tool service")
public class CapabilitiesCreateTool extends CapabilitiesBase {
    private static final Logger LOG = Logger.getLogger(CapabilitiesCreateTool.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        String baseCmd = config.tool().createCmd();
        createProject(baseCmd, "ai.wanaku.tool", "wanaku-tool-service");
        return EXIT_OK;
    }
}
