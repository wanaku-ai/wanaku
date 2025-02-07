package org.wanaku.routers.camel;

import jakarta.inject.Inject;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

@QuarkusMain(name = "base")
@CommandLine.Command(name = "camel", mixinStandardHelpOptions = true)
public class CamelRouterMain implements Runnable, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(names = {"--indexes-path"}, description = "The path to the index directory",
            defaultValue = "${user.home}/.wanaku/router/")
    private String indexesPath;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        Quarkus.run(CamelRouterMain.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this, factory).execute(args);
    }

    @Override
    public void run() {
        Quarkus.waitForExit();
    }
}
