package ai.wanaku.cli.main.commands.man;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(
        name = "man",
        description = "manual",
        subcommands = {LabelExpressionMan.class})
public class Man extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
