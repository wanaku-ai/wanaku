package org.wanaku.server.quarkus;

import jakarta.inject.Inject;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

@QuarkusMain(name = "base")
@CommandLine.Command(name = "server", mixinStandardHelpOptions = true)
public class ServerMain implements Runnable, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(names = {"--resources-path"}, description = "The path to the resources index",
            defaultValue = "${user.home}/.wanaku/server/")
    private String resourcesPath;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        Quarkus.run(ServerMain.class, args);
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
