package ai.wanaku.cli.main.commands.services;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;


@CommandLine.Command(name = "create",description = "Create a new service",
        subcommands = {ServicesCreateTool.class, ServicesCreateResources.class})
public class ServicesCreate extends BaseCommand {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
