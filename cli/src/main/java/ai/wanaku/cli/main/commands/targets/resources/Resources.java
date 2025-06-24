package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "resources",
        description = "Manage targets", subcommands = { ResourcesLinkedList.class, ResourcesConfigure.class, ResourcesState.class })
public class Resources extends BaseCommand {
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
