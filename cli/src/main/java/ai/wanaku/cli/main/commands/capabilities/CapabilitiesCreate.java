package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;


@CommandLine.Command(name = "create",description = "Create a new capability",
        subcommands = { CapabilitiesCreateTool.class, CapabilitiesCreateResources.class})
public class CapabilitiesCreate extends BaseCommand {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
