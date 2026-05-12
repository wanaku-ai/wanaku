package ai.wanaku.backend.api.v1.installations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import ai.wanaku.backend.api.v1.servicecatalog.ServiceCatalogBean;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

/**
 * Template-driven implementation of {@link ProcessInterface}.
 *
 * <p>Detects whether a catalog system is a Camel Integration Capability (CIC) or a
 * native Quarkus service by inspecting the catalog index, then builds the
 * appropriate command line for each type.
 *
 * <p>Executable paths come exclusively from configuration properties, never from
 * HTTP input, ensuring a security-first design.
 */
@ApplicationScoped
public class TemplateProcessInterface implements ProcessInterface {
    private static final Logger LOG = Logger.getLogger(TemplateProcessInterface.class);

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    @ConfigProperty(name = "wanaku.launcher.cic.executable", defaultValue = "camel-integration-capability-main.jar")
    String cicExecutable;

    @ConfigProperty(name = "wanaku.launcher.native.home", defaultValue = "${user.home}/.wanaku/local/")
    String nativeHome;

    /**
     * {@inheritDoc}
     *
     * <p>For CIC systems (those with a routes file), builds a {@code java -jar} command
     * pointing to the CIC executable with registration and gRPC arguments.
     *
     * <p>For native systems, builds a {@code java -jar} command pointing to the
     * system's {@code quarkus-run.jar} under the native home directory.
     *
     * @throws WanakuException if the catalog or system is not found
     */
    @Override
    public String[] buildCommand(String catalogName, String systemName, int grpcPort) {
        boolean isCic = isCicType(catalogName, systemName);

        if (isCic) {
            LOG.debugf("Building CIC command for %s/%s on port %d", catalogName, systemName, grpcPort);
            return new String[] {
                "java",
                "-jar",
                cicExecutable,
                "--registration-url",
                "http://localhost:8080",
                "--registration-announce-address",
                "localhost",
                "--grpc-port",
                String.valueOf(grpcPort),
                "--name",
                systemName,
                "--service-catalog",
                catalogName,
                "--service-catalog-system",
                systemName,
                "--client-id",
                "wanaku-service",
                "--fail-fast"
            };
        } else {
            LOG.debugf("Building native command for %s/%s on port %d", catalogName, systemName, grpcPort);
            String resolvedHome = resolveNativeHome();
            String jarPath = resolvedHome + "/" + systemName + "/quarkus-run.jar";
            return new String[] {
                "java", "-Dquarkus.grpc.server.port=" + grpcPort, "-Dquarkus.profile=noauth", "-jar", jarPath
            };
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Currently returns an empty map. Can be extended to pass environment
     * variables to launched processes.
     */
    @Override
    public Map<String, String> buildEnvironment(String catalogName, String systemName) {
        return Collections.emptyMap();
    }

    /**
     * {@inheritDoc}
     *
     * <p>For CIC systems, returns the parent directory of the CIC executable.
     * For native systems, returns the system subdirectory under the native home.
     */
    @Override
    public File getWorkingDirectory(String catalogName, String systemName) {
        boolean isCic = isCicType(catalogName, systemName);

        if (isCic) {
            File execFile = new File(cicExecutable);
            File parent = execFile.getParentFile();
            return parent != null ? parent : new File(".");
        } else {
            String resolvedHome = resolveNativeHome();
            return new File(resolvedHome, systemName);
        }
    }

    /**
     * Determines whether a system within a catalog is a CIC type by checking
     * if it has a routes file declared in the catalog index.
     *
     * @param catalogName the catalog name
     * @param systemName  the system name
     * @return {@code true} if the system has a routes file (CIC type)
     */
    private boolean isCicType(String catalogName, String systemName) {
        DataStore catalog = serviceCatalogBean.get(catalogName);
        if (catalog == null) {
            throw new WanakuException("Service catalog not found: " + catalogName);
        }

        ServiceCatalogIndex index = serviceCatalogBean.parseIndex(catalog);
        return index.getRoutesFile(systemName) != null;
    }

    /**
     * Resolves the {@code ${user.home}} placeholder in the native home path.
     *
     * @return the resolved native home path
     */
    private String resolveNativeHome() {
        return nativeHome.replace("${user.home}", System.getProperty("user.home"));
    }
}
