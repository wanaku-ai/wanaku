package ai.wanaku.cli.main.commands.services;

import jakarta.inject.Inject;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import org.jboss.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "resource",description = "Create a new resource provider capability")
public class CapabilitiesCreateResources extends CapabilitiesBase {
    private static final Logger LOG = Logger.getLogger(CapabilitiesCreateResources.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public void run() {
        String baseCmd = config.resource().createCmd();

        createProject(baseCmd, "ai.wanaku.provider", "wanaku-provider");
    }

}
