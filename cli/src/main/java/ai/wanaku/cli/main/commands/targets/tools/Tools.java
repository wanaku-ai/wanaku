package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "tools",
        description = "Manage targets", subcommands = { ToolsLinkedList.class, ToolsConfigure.class, ToolsState.class })
public class Tools extends BaseCommand {
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
