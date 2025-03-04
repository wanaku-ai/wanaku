package ai.wanaku.cli.main.commands.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "resources",
        description = "Manage resources", subcommands = { ResourcesExpose.class, ResourcesRemove.class, ResourcesList.class})
public class Resources extends BaseCommand {

    @Override
    public void run() {

    }
}
