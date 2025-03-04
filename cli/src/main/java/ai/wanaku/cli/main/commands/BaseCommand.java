package ai.wanaku.cli.main.commands;

import picocli.CommandLine;

public abstract class BaseCommand implements Runnable {

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
}
