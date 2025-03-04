package ai.wanaku.cli.main.commands.tools;

import picocli.CommandLine;

@CommandLine.Command(name = "tools",
        description = "Manage tools", subcommands = { ToolsAdd.class, ToolsRemove.class, ToolsList.class, ToolsImport.class })
public class Tools {
}
