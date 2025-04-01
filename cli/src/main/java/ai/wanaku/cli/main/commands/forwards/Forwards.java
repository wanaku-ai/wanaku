package ai.wanaku.cli.main.commands.forwards;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "forwards",
        description = "Manage forwards", subcommands = { ForwardsAdd.class, ForwardsRemove.class, ForwardsList.class })
public class Forwards extends BaseCommand {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
