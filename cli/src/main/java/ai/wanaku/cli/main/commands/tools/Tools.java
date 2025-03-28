package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "tools",
        description = "Manage tools", subcommands = { ToolsAdd.class, ToolsRemove.class, ToolsList.class, ToolsImport.class, ToolsGenerate.class})
public class Tools extends BaseCommand {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
