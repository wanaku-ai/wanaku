package ai.wanaku.cli.main.commands.forwards;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;

import java.io.IOException;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.usage;

@Command(name = "forwards",
        description = "Manage forwards", subcommands = { ForwardsAdd.class, ForwardsRemove.class, ForwardsList.class })
public class Forwards extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
