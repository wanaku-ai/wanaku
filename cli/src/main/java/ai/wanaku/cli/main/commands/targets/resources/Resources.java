package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "resources",
        description = "Manage targets", subcommands = { ResourcesLinkedList.class, ResourcesConfigure.class })
public class Resources extends BaseCommand {
    @Override
    public void run() {

    }
}
