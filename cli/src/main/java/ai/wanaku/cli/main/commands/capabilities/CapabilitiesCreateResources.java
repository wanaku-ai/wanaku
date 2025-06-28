package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.support.WanakuPrinter;
import jakarta.inject.Inject;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.io.IOException;

@CommandLine.Command(name = "resource",description = "Create a new resource provider capability")
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
