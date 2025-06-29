package ai.wanaku.cli.main.commands.namespaces;

import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "namespaces",
        description = "Manage namespaces", subcommands = { NamespaceList.class})
public class Namespaces extends BaseCommand {
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
