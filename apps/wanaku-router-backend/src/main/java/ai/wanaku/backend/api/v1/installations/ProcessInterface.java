package ai.wanaku.backend.api.v1.installations;

import java.io.File;
import java.util.Map;

/**
 * Defines how to construct the command line, environment, and working directory
 * for a capability process.
 *
 * <p>Implementations translate a catalog/system pair into the concrete OS-level
 * details needed by {@link SubProcessLauncher} to start an external process.
 */
public interface ProcessInterface {

    /**
     * Builds the command array to execute for the given system.
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @param grpcPort    the gRPC port allocated for this process
     * @return the command and arguments as a string array; never null
     */
    String[] buildCommand(String catalogName, String systemName, int grpcPort);

    /**
     * Builds environment variables for the process.
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @return a map of environment variable names to values; may be empty, never null
     */
    Map<String, String> buildEnvironment(String catalogName, String systemName);

    /**
     * Returns the working directory for the process.
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @return the working directory; never null
     */
    File getWorkingDirectory(String catalogName, String systemName);
}
