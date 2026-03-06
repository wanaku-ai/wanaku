package ai.wanaku.cli.main;

import jakarta.inject.Inject;

import java.util.concurrent.Callable;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.QuarkusApplication;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.commands.admin.Admin;
import ai.wanaku.cli.main.commands.auth.Auth;
import ai.wanaku.cli.main.commands.capabilities.Capabilities;
import ai.wanaku.cli.main.commands.completion.Completion;
import ai.wanaku.cli.main.commands.datastores.DataStores;
import ai.wanaku.cli.main.commands.forwards.Forwards;
import ai.wanaku.cli.main.commands.man.Man;
import ai.wanaku.cli.main.commands.namespaces.Namespaces;
import ai.wanaku.cli.main.commands.prompts.Prompts;
import ai.wanaku.cli.main.commands.resources.Resources;
import ai.wanaku.cli.main.commands.service.Service;
import ai.wanaku.cli.main.commands.start.Start;
import ai.wanaku.cli.main.commands.tools.Tools;
import ai.wanaku.cli.main.commands.toolset.ToolSet;
import ai.wanaku.cli.main.support.WanakuExceptionHandler;
import ai.wanaku.core.util.VersionHelper;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
        name = "wanaku",
        subcommands = {
            Admin.class,
            Auth.class,
            Forwards.class,
            Resources.class,
            Prompts.class,
            Start.class,
            Capabilities.class,
            Tools.class,
            ToolSet.class,
            Namespaces.class,
            Man.class,
            Completion.class,
            DataStores.class,
            Service.class
        })
public class CliMain implements Callable<Integer>, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Display the help and sub-commands")
    private boolean helpRequested = false;

    @CommandLine.Option(
            names = {"-v", "--version"},
            description = "Display the current version of Wanaku CLI")
    private boolean versionRequested = false;

    @CommandLine.Option(
            names = {"--verbose"},
            description = "Display detailed error messages including stack traces",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose = false;

    @Override
    public int run(String... args) throws Exception {
        CommandLine commandLine = new CommandLine(this, factory);
        commandLine.setExecutionExceptionHandler(new WanakuExceptionHandler());
        return commandLine.execute(args);
    }

    @Override
    public Integer call() {
        if (versionRequested) {
            System.out.println("Wanaku CLI version " + VersionHelper.VERSION);
        }

        CommandLine.usage(this, System.out);
        return BaseCommand.EXIT_ERROR;
    }
}
