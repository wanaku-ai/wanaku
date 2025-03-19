package ai.wanaku.cli.main.commands.services;

import jakarta.inject.Inject;

import ai.wanaku.cli.main.support.WanakuCliConfig;
import org.jboss.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "resource",description = "Create a new resource service")
public class ServicesCreateResources extends ServicesBase {
    private static final Logger LOG = Logger.getLogger(ServicesCreateResources.class);

    @Inject
    WanakuCliConfig config;

    @Override
    public void run() {
        String baseCmd = config.tool().createCmd();

        createProject(baseCmd);
    }

}
