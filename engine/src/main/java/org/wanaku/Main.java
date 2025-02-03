package org.wanaku;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "myapp", description = "My application")
public class Main implements Runnable {
    @Option(names = {"--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = {"--list"}, description = "List something.")
    private boolean listRequested;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (helpRequested) {
            System.out.println("Help requested. Showing usage:");
        }

        if (listRequested) {
            System.out.println("Listing items...");
        }
    }
}