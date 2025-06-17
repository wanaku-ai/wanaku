package ai.wanaku.cli.main.commands.services;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "capabilities",
        description = "Manage capabilities", subcommands = { CapabilitiesCreate.class})
public class Capabilities extends BaseCommand {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}