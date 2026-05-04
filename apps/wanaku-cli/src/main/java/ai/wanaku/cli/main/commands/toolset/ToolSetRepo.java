package ai.wanaku.cli.main.commands.toolset;

import java.io.IOException;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "repo",
        description = "Manage toolset repositories",
        subcommands = {ToolSetRepoAdd.class, ToolSetRepoList.class, ToolSetRepoRemove.class, ToolSetRepoBrowse.class})
public class ToolSetRepo extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
