package ai.wanaku.cli.main.commands.start;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "start",
        description = "Start Wanaku", subcommands = { StartLocal.class})
public class Start extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}