package ai.wanaku.cli.main.commands;

import picocli.CommandLine;

import java.util.concurrent.Callable;

public abstract class BaseCommand implements Callable<Integer> {

    public static final int  EXIT_OK = 0;
    public static final int  EXIT_ERROR = 1;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
}
