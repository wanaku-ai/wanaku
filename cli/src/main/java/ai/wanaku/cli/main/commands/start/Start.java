package ai.wanaku.cli.main.commands.start;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "start",
        description = "Start Wanaku", subcommands = { StartLocal.class})
public class Start extends BaseCommand {

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}