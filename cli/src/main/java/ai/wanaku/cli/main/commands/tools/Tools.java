package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(
        name = "tools",
        description = "Manage tools",
        subcommands = {
            ToolsEdit.class,
            ToolsAdd.class,
            ToolsRemove.class,
            ToolsList.class,
            ToolsImport.class,
            ToolsGenerate.class
        })
public class Tools extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
