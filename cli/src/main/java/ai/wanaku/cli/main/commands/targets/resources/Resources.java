package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "resources",
        description = "Manage targets", subcommands = { ResourcesLinkedList.class, ResourcesConfigure.class, ResourcesState.class })
@Deprecated
public class Resources extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }

}
