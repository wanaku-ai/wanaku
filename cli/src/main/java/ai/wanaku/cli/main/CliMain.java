package ai.wanaku.cli.main;

import jakarta.inject.Inject;

import ai.wanaku.cli.main.commands.forwards.Forwards;
import ai.wanaku.cli.main.commands.resources.Resources;
import ai.wanaku.cli.main.commands.services.Capabilities;
import ai.wanaku.cli.main.commands.start.Start;
import ai.wanaku.cli.main.commands.targets.Targets;
import ai.wanaku.cli.main.commands.tools.Tools;
import ai.wanaku.cli.main.commands.toolset.ToolSet;
import ai.wanaku.core.util.VersionHelper;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.QuarkusApplication;
import picocli.CommandLine;



@TopCommand
@CommandLine.Command(name = "wanaku", subcommands = { Forwards.class, Resources.class, Start.class, Capabilities.class, Targets.class, Tools.class, ToolSet.class,  })
public class CliMain implements Runnable, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;

    @CommandLine.Option(names = { "-v", "--version" }, description = "Display the current version of Wanaku CLI")
    private boolean versionRequested = false;

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this, factory).execute(args);
    }

    @Override
    public void run() {
        if (versionRequested) {
            System.out.println("Wanaku CLI version " + VersionHelper.VERSION);
        }

        CommandLine.usage(this, System.out);
    }
}
