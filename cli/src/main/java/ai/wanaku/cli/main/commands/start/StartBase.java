package ai.wanaku.cli.main.commands.start;

import ai.wanaku.cli.main.commands.BaseCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import java.util.List;

public abstract class StartBase extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(StartBase.class);

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    ExclusiveOptions exclusive;

    static class ExclusiveOptions {
        @CommandLine.Option(names = { "--services" }, split = ",", description = "Which of services to start (separated by comma)", arity = "0..n")
        protected List<String> services;

        @CommandLine.Option(names = { "--list-services" }, description = "A list of available services")
        protected boolean listServices = false;

        @CommandLine.Option(names = { "--clean" }, description = "Clean the local cache of downloaded files and services")
        protected boolean clean = false;
    }

    protected abstract void startWanaku();
}
