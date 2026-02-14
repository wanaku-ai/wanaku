package ai.wanaku.cli.main.commands.capabilities;

import jakarta.inject.Inject;

import java.io.IOException;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "resource", description = "Create a new resource provider capability")
public class CapabilitiesCreateResources extends CapabilitiesBase {
    private static final Logger LOG = Logger.getLogger(CapabilitiesCreateResources.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        String baseCmd = config.resource().createCmd();
        createProject(baseCmd, "ai.wanaku.provider", "wanaku-provider");
        return EXIT_OK;
    }
}
