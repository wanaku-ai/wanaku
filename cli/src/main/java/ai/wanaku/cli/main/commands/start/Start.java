package ai.wanaku.cli.main.commands.start;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "start",
        description = "Start Wanaku", subcommands = { StartLocal.class})
public class Start extends BaseCommand {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}