package ai.wanaku.cli.main.commands.targets;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.commands.targets.resources.Resources;
import ai.wanaku.cli.main.commands.targets.tools.Tools;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(
        name = "targets",
        description = "Manage targets",
        subcommands = {Tools.class, Resources.class})
@Deprecated
public class Targets extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
