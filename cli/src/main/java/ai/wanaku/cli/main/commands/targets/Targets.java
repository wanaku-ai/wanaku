package ai.wanaku.cli.main.commands.targets;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.commands.targets.resources.Resources;
import ai.wanaku.cli.main.commands.targets.tools.Tools;
import picocli.CommandLine;

@CommandLine.Command(name = "targets",
        description = "Manage targets", subcommands = { Tools.class, Resources.class})
public class Targets extends BaseCommand {
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
