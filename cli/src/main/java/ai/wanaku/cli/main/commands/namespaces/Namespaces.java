package ai.wanaku.cli.main.commands.namespaces;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import static picocli.CommandLine.usage;

@CommandLine.Command(name = "namespaces",
        description = "Manage namespaces", subcommands = { NamespaceList.class})
public class Namespaces extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
