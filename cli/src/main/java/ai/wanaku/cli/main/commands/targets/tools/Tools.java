package ai.wanaku.cli.main.commands.targets.tools;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.usage;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;

@Deprecated
@Command(
        name = "tools",
        description = "Manage targets",
        subcommands = {ToolsLinkedList.class, ToolsConfigure.class, ToolsState.class})
public class Tools extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
