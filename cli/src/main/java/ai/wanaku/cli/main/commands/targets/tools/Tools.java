package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "tools",
        description = "Manage targets", subcommands = { ToolsLinkedList.class, ToolsConfigure.class })
public class Tools extends BaseCommand {
    @Override
    public void run() {

    }
}
