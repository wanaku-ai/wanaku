package ai.wanaku.cli.main.commands.services;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "services",
        description = "Manage services", subcommands = {ServicesCreate.class})
public class Services extends BaseCommand {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}