package ai.wanaku.cli.main.commands.start;

import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.runner.local.LocalRunner;
import picocli.CommandLine;

@CommandLine.Command(name = "local", description = "Create a new tool service")
public class StartLocal extends StartBase {

    private static final Logger LOG = Logger.getLogger(StartLocal.class);

    @Inject
    WanakuCliConfig config;

    @CommandLine.Option(
            names = {"--local-dist"},
            description =
                    "Path to a locally built ZIP distribution (repeatable, overrides download for matching components)",
            arity = "1")
    protected List<File> localDists;

    static class CamelRoutesConfig {
        @CommandLine.Option(
                names = {"--camel-routes"},
                required = true,
                description =
                        "Path to Apache Camel routes YAML file for the Camel Integration Capability. Supports file:// scheme (e.g., file:///path/to/routes.camel.yaml)")
        String camelRoutes;

        @CommandLine.Option(
                names = {"--camel-rules"},
                required = true,
                description =
                        "Path to route exposure rules YAML file for the Camel Integration Capability. Supports file:// scheme (e.g., file:///path/to/rules.yaml)")
        String camelRules;
    }

    static class ServiceCatalogConfig {
        @CommandLine.Option(
                names = {"--service-catalog"},
                required = true,
                description = "Name of the service catalog to use for the Camel Integration Capability")
        String serviceCatalog;

        @CommandLine.Option(
                names = {"--service-catalog-system"},
                required = true,
                description = "The system name within the service catalog to use (e.g., ftp)")
        String serviceCatalogSystem;
    }

    static class CicSourceConfig {
        @CommandLine.ArgGroup(exclusive = false)
        CamelRoutesConfig camelRoutesConfig;

        @CommandLine.ArgGroup(exclusive = false)
        ServiceCatalogConfig serviceCatalogConfig;
    }

    @CommandLine.ArgGroup(exclusive = true)
    protected CicSourceConfig cicSourceConfig;

    @CommandLine.Option(
            names = {"--fail-fast"},
            description = "Fail fast if route loading fails in the Camel Integration Capability",
            defaultValue = "true")
    protected boolean failFast;

    @Override
    protected void startWanaku() {
        List<String> services;
        if (exclusive != null && exclusive.services != null) {
            services = exclusive.services;

        } else {
            services = config.defaultServices();
        }

        LocalRunner.LocalRunnerEnvironment environment = new LocalRunner.LocalRunnerEnvironment();
        if (localDists != null) {
            for (File dist : localDists) {
                environment.withLocalDist(dist);
            }
        }

        if (cicSourceConfig != null) {
            if (cicSourceConfig.camelRoutesConfig != null) {
                environment.withCamelRoutes(cicSourceConfig.camelRoutesConfig.camelRoutes);
                environment.withCamelRules(cicSourceConfig.camelRoutesConfig.camelRules);
            }

            if (cicSourceConfig.serviceCatalogConfig != null) {
                environment.withServiceCatalog(cicSourceConfig.serviceCatalogConfig.serviceCatalog);
                environment.withServiceCatalogSystem(cicSourceConfig.serviceCatalogConfig.serviceCatalogSystem);
            }
        }

        environment.withFailFast(failFast);

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
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
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
