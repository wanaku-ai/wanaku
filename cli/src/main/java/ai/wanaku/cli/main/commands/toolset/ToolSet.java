package ai.wanaku.cli.main.commands.toolset;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "toolset",
        description = "Manage toolsets", subcommands = { ToolSetAdd.class})
public class ToolSet extends BaseCommand {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
