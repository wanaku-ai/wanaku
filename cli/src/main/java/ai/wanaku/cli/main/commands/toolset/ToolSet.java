package ai.wanaku.cli.main.commands.toolset;

import picocli.CommandLine;

@CommandLine.Command(name = "toolset",
        description = "Manage toolsets", subcommands = { ToolSetAdd.class})
public class ToolSet {
}
