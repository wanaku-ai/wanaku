package ai.wanaku.cli.main.commands.services;

import jakarta.inject.Inject;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import org.jboss.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "tool",description = "Create a new tool service")
public class CapabilitiesCreateTool extends CapabilitiesBase {
    private static final Logger LOG = Logger.getLogger(CapabilitiesCreateTool.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public void run() {
        String baseCmd = config.tool().createCmd();

        createProject(baseCmd, "ai.wanaku.tool", "wanaku-tool-service");
    }
}
