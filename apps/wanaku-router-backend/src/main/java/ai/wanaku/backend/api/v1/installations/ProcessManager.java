package ai.wanaku.backend.api.v1.installations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;
import io.quarkus.runtime.ShutdownEvent;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.common.ProcessRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages the lifecycle of capability processes launched through the installations API.
 *
 * <p>Each installation is identified by a unique ID. When launched, a process is started
 * in a dedicated daemon thread and assigned a dynamically allocated gRPC port. The manager
 * tracks running processes and ensures all are terminated on application shutdown.
 */
@ApplicationScoped
public class ProcessManager {
    private static final Logger LOG = Logger.getLogger(ProcessManager.class);

    private static final int PORT_RANGE_START = 9001;
    private static final int PORT_RANGE_END = 11999;
    private static final int MAX_PORT_ALLOCATION_ATTEMPTS = 50;

    private final ConcurrentHashMap<String, ManagedProcess> managedProcesses = new ConcurrentHashMap<>();
    private final Set<Integer> usedPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Internal representation of a running process and its metadata.
     */
    static class ManagedProcess {
        final Thread thread;
        final int grpcPort;
        final Instant startedAt;
        volatile boolean stopped;

        ManagedProcess(Thread thread, int grpcPort, Instant startedAt) {
            this.thread = thread;
            this.grpcPort = grpcPort;
            this.startedAt = startedAt;
            this.stopped = false;
        }
    }

    /**
     * Allocates an available gRPC port by randomly picking from the range 9001-11999.
     *
     * <p>The method checks both the internal used-ports set and attempts to bind to the port
     * via a {@link ServerSocket} to ensure it is truly available. Retries up to
     * {@value #MAX_PORT_ALLOCATION_ATTEMPTS} times before throwing.
     *
     * @return an available port number
     * @throws WanakuException if no port could be allocated after all attempts
     */
    int allocatePort() {
        for (int attempt = 0; attempt < MAX_PORT_ALLOCATION_ATTEMPTS; attempt++) {
            int port = ThreadLocalRandom.current().nextInt(PORT_RANGE_START, PORT_RANGE_END + 1);

            if (usedPorts.contains(port)) {
                continue;
            }

            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                usedPorts.add(port);
                return port;
            } catch (IOException e) {
                LOG.debugf(
                        "Port %d is not available, retrying (attempt %d/%d)",
                        port, attempt + 1, MAX_PORT_ALLOCATION_ATTEMPTS);
            }
        }

        throw new WanakuException("Failed to allocate a gRPC port after " + MAX_PORT_ALLOCATION_ATTEMPTS + " attempts");
    }

    /**
     * Launches a capability process for the given installation.
     *
     * <p>The process configuration is read from the JSON {@code jsonConfig} string, which must
     * contain at least a {@code type} and {@code executablePath}. Depending on the type, additional
     * fields may be required (e.g., {@code serviceCatalog} for CIC type).
     *
     * @param installationId the unique installation identifier
     * @param name           the human-readable name of the installation
     * @param jsonConfig     the JSON configuration string from the DataStore data field
     * @return the status of the newly launched process
     * @throws WanakuException if the installation is already running or the configuration is invalid
     */
    public ProcessStatus launch(String installationId, String name, String jsonConfig) {
        ManagedProcess existing = managedProcesses.get(installationId);
        if (existing != null && existing.thread.isAlive()) {
            throw new WanakuException("Installation " + installationId + " is already running");
        }

        JsonNode config;
        try {
            config = objectMapper.readTree(jsonConfig);
        } catch (Exception e) {
            throw new WanakuException("Invalid JSON configuration: " + e.getMessage());
        }

        String type = getRequiredField(config, "type");
        String executablePath = getRequiredField(config, "executablePath");

        int port = allocatePort();
        File workingDir = new File(executablePath).getParentFile();

        if (workingDir == null) {
            throw new WanakuException("Cannot determine working directory from executable path: " + executablePath);
        }

        Map<String, String> envVars = parseEnvironmentVariables(config);
        String[] command = buildCommand(type, executablePath, port, name, config);

        Instant startedAt = Instant.now();

        Thread thread = new Thread(() -> {
            try {
                ProcessRunner.run(workingDir, envVars, command);
            } catch (Exception e) {
                LOG.warnf("Process for installation %s exited: %s", installationId, e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("wanaku-installation-" + installationId);
        thread.start();

        ManagedProcess managed = new ManagedProcess(thread, port, startedAt);
        managedProcesses.put(installationId, managed);

        LOG.infof("Launched installation %s (name=%s, type=%s, port=%d)", installationId, name, type, port);

        return new ProcessStatus(true, port, startedAt.toString(), null);
    }

    /**
     * Stops a running capability process.
     *
     * @param installationId the unique installation identifier
     * @throws WanakuException if the installation is not found in managed processes
     */
    public void stop(String installationId) {
        ManagedProcess managed = managedProcesses.get(installationId);
        if (managed == null) {
            throw new WanakuException("No managed process found for installation: " + installationId);
        }

        managed.stopped = true;
        managed.thread.interrupt();
        managedProcesses.remove(installationId);
        usedPorts.remove(managed.grpcPort);

        LOG.infof("Stopped installation %s (port=%d)", installationId, managed.grpcPort);
    }

    /**
     * Returns the current status of an installation process.
     *
     * @param installationId the unique installation identifier
     * @return the process status; if not tracked, returns a status indicating not running
     */
    public ProcessStatus getStatus(String installationId) {
        ManagedProcess managed = managedProcesses.get(installationId);
        if (managed == null) {
            return new ProcessStatus(false, 0, null, null);
        }

        if (managed.thread.isAlive()) {
            return new ProcessStatus(true, managed.grpcPort, managed.startedAt.toString(), null);
        }

        return new ProcessStatus(false, managed.grpcPort, managed.startedAt.toString(), null);
    }

    /**
     * Checks whether an installation process is currently running.
     *
     * @param installationId the unique installation identifier
     * @return {@code true} if the process is alive
     */
    public boolean isRunning(String installationId) {
        ManagedProcess managed = managedProcesses.get(installationId);
        return managed != null && managed.thread.isAlive();
    }

    /**
     * Shuts down all managed processes on application shutdown.
     *
     * @param event the Quarkus shutdown event
     */
    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Shutting down all managed installation processes");
        for (Map.Entry<String, ManagedProcess> entry : managedProcesses.entrySet()) {
            LOG.infof("Interrupting installation process: %s", entry.getKey());
            entry.getValue().stopped = true;
            entry.getValue().thread.interrupt();
        }
        managedProcesses.clear();
        usedPorts.clear();
    }

    private String getRequiredField(JsonNode config, String fieldName) {
        JsonNode node = config.get(fieldName);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new WanakuException("Missing required configuration field: " + fieldName);
        }
        return node.asText();
    }

    private String getOptionalField(JsonNode config, String fieldName) {
        JsonNode node = config.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseEnvironmentVariables(JsonNode config) {
        JsonNode envNode = config.get("environmentVariables");
        if (envNode == null || envNode.isNull()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.treeToValue(envNode, Map.class);
        } catch (Exception e) {
            LOG.warnf("Failed to parse environment variables, using empty map: %s", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String[] buildCommand(String type, String executablePath, int port, String name, JsonNode config) {
        switch (type) {
            case "native":
                return new String[] {
                    "java", "-Dquarkus.grpc.server.port=" + port, "-Dquarkus.profile=noauth", "-jar", executablePath
                };
            case "cic":
                String serviceCatalog = getRequiredField(config, "serviceCatalog");
                String serviceCatalogSystem = getRequiredField(config, "serviceCatalogSystem");
                return new String[] {
                    "java",
                    "-jar",
                    executablePath,
                    "--registration-url",
                    "http://localhost:8080",
                    "--registration-announce-address",
                    "localhost",
                    "--grpc-port",
                    String.valueOf(port),
                    "--name",
                    name,
                    "--service-catalog",
                    serviceCatalog,
                    "--service-catalog-system",
                    serviceCatalogSystem,
                    "--client-id",
                    "wanaku-service",
                    "--fail-fast"
                };
            case "code-execution":
                return new String[] {
                    "java", "-Dquarkus.grpc.server.port=" + port, "-Dquarkus.profile=noauth", "-jar", executablePath
                };
            default:
                throw new WanakuException("Unsupported installation type: " + type);
        }
    }
}
