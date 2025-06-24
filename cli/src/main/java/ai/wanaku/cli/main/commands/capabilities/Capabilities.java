package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "capabilities",
        description = "Manage capabilities", subcommands = { CapabilitiesList.class, CapabilitiesCreate.class, CapabilitiesShow.class })
public class Capabilities extends BaseCommand {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}