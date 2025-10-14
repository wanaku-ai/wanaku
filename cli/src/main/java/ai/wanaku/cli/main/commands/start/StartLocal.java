package ai.wanaku.cli.main.commands.start;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.runner.local.LocalRunner;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "local", description = "Create a new tool service")
public class StartLocal extends StartBase {

    private static final Logger LOG = Logger.getLogger(StartLocal.class);

    @Inject
    WanakuCliConfig config;

    @Override
    protected void startWanaku() {
        List<String> services;
        if (exclusive != null && exclusive.services != null) {
            services = exclusive.services;

        } else {
            services = config.defaultServices();
        }

        LocalRunner.LocalRunnerEnvironment environment = new LocalRunner.LocalRunnerEnvironment()
                .withServiceOption("-Dquarkus.oidc.client.credentials.secret", capabilitiesClientSecret);

        LocalRunner localRunner = new LocalRunner(config, environment);
        try {
            localRunner.start(services);
        } catch (WanakuException | IOException e) {
            LOG.errorf(e, e.getMessage());
        }
    }

    public static void deleteDirectory(WanakuPrinter printer, File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : Objects.requireNonNull(children)) {
                File childDir = new File(dir, child);
                deleteDirectory(printer, childDir);
            }
        }
        if (!dir.delete()) {
            printer.printErrorMessage("Failed to delete " + dir);
        }
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (exclusive != null && exclusive.exclusiveNonStart != null) {
            if (exclusive.exclusiveNonStart.listServices) {
                Map<String, String> components = config.components();
                for (String component : components.keySet()) {
                    if (!component.startsWith("wanaku-router")) {
                        printer.printInfoMessage(" - " + component);
                    }
                }
                return EXIT_OK;
            }

            if (exclusive.exclusiveNonStart.clean) {
                printer.printWarningMessage("Removing Wanaku cache directory");
                deleteDirectory(printer, new File(RuntimeConstants.WANAKU_CACHE_DIR));

                printer.printWarningMessage("Removing Wanaku local instance directory");
                deleteDirectory(printer, new File(RuntimeConstants.WANAKU_LOCAL_DIR));

                return EXIT_OK;
            }
        }
        startWanaku();
        return EXIT_OK;
    }
}
