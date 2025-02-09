package org.wanaku.cli.main.commands.tools;

import org.wanaku.cli.main.commands.resources.ResourcesExpose;
import picocli.CommandLine;

@CommandLine.Command(name = "tools",
        description = "Manage tools", subcommands = { ToolsAdd.class })
public class Tools {
}
