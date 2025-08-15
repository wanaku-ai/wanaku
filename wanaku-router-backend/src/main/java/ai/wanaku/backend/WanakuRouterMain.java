package ai.wanaku.backend;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

/**
 * Main class for the router
 */
@QuarkusMain(name = "base")
@CommandLine.Command(name = "camel", mixinStandardHelpOptions = true)
public class WanakuRouterMain implements Runnable, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "display a help message")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        Quarkus.run(WanakuRouterMain.class, args);
    }

    @Override
    public int run(String... args) {
        return new CommandLine(this, factory).execute(args);
    }

    @Override
    public void run() {
        Quarkus.waitForExit();
    }
}
