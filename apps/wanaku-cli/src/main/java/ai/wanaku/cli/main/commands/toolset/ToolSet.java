package ai.wanaku.cli.main.commands.toolset;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "toolset",
        description = "Manage toolsets",
        subcommands = {ToolSetAdd.class, ToolSetRepo.class})
public class ToolSet extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
