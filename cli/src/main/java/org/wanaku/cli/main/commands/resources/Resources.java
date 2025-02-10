package org.wanaku.cli.main.commands.resources;

import org.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "resources",
        description = "Manage resources", subcommands = { ResourcesExpose.class, ResourcesList.class})
public class Resources extends BaseCommand {

    @Override
    public void run() {

    }
}
