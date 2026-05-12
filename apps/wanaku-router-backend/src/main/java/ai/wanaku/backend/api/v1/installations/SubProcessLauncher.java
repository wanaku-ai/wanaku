package ai.wanaku.backend.api.v1.installations;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.common.ProcessRunner;

/**
 * A {@link Launcher} implementation that manages capability processes as
 * OS-level subprocesses.
 *
 * <p>Each launched process runs in a daemon thread and is tracked by a composite
 * key of {@code catalogName:systemName}. Ports are dynamically allocated in the
 * range 9001--11999 and released when the process is stopped.
 *
 * <p>All running processes are interrupted on application shutdown.
 */
public class SubProcessLauncher implements Launcher {
    private static final Logger LOG = Logger.getLogger(SubProcessLauncher.class);

    private static final int PORT_RANGE_START = 9001;
    private static final int PORT_RANGE_END = 11999;
    private static final int MAX_PORT_ATTEMPTS = 50;

    private final ProcessInterface processInterface;
    private final ConcurrentHashMap<String, ManagedProcess> managedProcesses = new ConcurrentHashMap<>();
    private final Set<Integer> usedPorts = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a SubProcessLauncher with the given process interface.
     *
     * @param processInterface the interface used to build commands, environments, and working directories
     */
    public SubProcessLauncher(ProcessInterface processInterface) {
        this.processInterface = processInterface;
    }

    /**
     * {@inheritDoc}
     *
     * @throws WanakuException if the process is already running or port allocation fails
     */
    @Override
    public ProcessStatus launch(String catalogName, String systemName) {
        String key = buildKey(catalogName, systemName);

        ManagedProcess existing = managedProcesses.get(key);
        if (existing != null && existing.thread.isAlive()) {
            throw new WanakuException("Process already running for " + key);
        }

        int port = allocatePort();
        String[] command = processInterface.buildCommand(catalogName, systemName, port);
        Map<String, String> envVars = processInterface.buildEnvironment(catalogName, systemName);
        File workingDir = processInterface.getWorkingDirectory(catalogName, systemName);

        Instant startedAt = Instant.now();

        Thread thread = new Thread(
                () -> {
                    try {
                        LOG.infof("Starting process for %s on port %d", key, port);
                        ProcessRunner.run(workingDir, envVars, command);
                    } catch (Exception e) {
                        LOG.errorf(e, "Process %s terminated with error", key);
                    } finally {
                        LOG.infof("Process %s has exited", key);
                    }
                },
                "launcher-" + key);
        thread.setDaemon(true);
        thread.start();

        ManagedProcess managed = new ManagedProcess(thread, port, startedAt);
        managedProcesses.put(key, managed);

        LOG.infof("Launched process %s on port %d", key, port);
        return new ProcessStatus(true, port, startedAt.toString(), null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws WanakuException if no process is found for the given catalog/system
     */
    @Override
    public void stop(String catalogName, String systemName) {
        String key = buildKey(catalogName, systemName);
        ManagedProcess managed = managedProcesses.remove(key);

        if (managed == null) {
            throw new WanakuException("No running process found for " + key);
        }

        managed.stopped = true;
        managed.thread.interrupt();
        usedPorts.remove(managed.grpcPort);
        LOG.infof("Stopped process %s (port %d)", key, managed.grpcPort);
    }

    /** {@inheritDoc} */
    @Override
    public ProcessStatus getStatus(String catalogName, String systemName) {
        String key = buildKey(catalogName, systemName);
        ManagedProcess managed = managedProcesses.get(key);

        if (managed == null) {
            return new ProcessStatus(false, 0, null, null);
        }

        if (managed.thread.isAlive()) {
            return new ProcessStatus(true, managed.grpcPort, managed.startedAt.toString(), null);
        } else {
            // Thread has died; clean up
            managedProcesses.remove(key);
            usedPorts.remove(managed.grpcPort);
            return new ProcessStatus(false, managed.grpcPort, managed.startedAt.toString(), null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, ProcessStatus> getAllStatuses() {
        Map<String, ProcessStatus> statuses = new HashMap<>();
        for (Map.Entry<String, ManagedProcess> entry : managedProcesses.entrySet()) {
            ManagedProcess managed = entry.getValue();
            boolean alive = managed.thread.isAlive();

            statuses.put(
                    entry.getKey(), new ProcessStatus(alive, managed.grpcPort, managed.startedAt.toString(), null));

            if (!alive) {
                managedProcesses.remove(entry.getKey());
                usedPorts.remove(managed.grpcPort);
            }
        }
        return Collections.unmodifiableMap(statuses);
    }

    /**
     * Shuts down all managed processes. Called by {@link LauncherProvider}
     * when the application stops.
     */
    public void shutdown() {
        LOG.infof("Shutting down %d managed process(es)", managedProcesses.size());
        for (Map.Entry<String, ManagedProcess> entry : managedProcesses.entrySet()) {
            entry.getValue().stopped = true;
            entry.getValue().thread.interrupt();
            LOG.debugf("Interrupted process %s", entry.getKey());
        }
        managedProcesses.clear();
        usedPorts.clear();
    }

    /**
     * Allocates a random unused port in the range [{@value PORT_RANGE_START}, {@value PORT_RANGE_END}].
     *
     * @return the allocated port number
     * @throws WanakuException if no free port is found after {@value MAX_PORT_ATTEMPTS} attempts
     */
    private int allocatePort() {
        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int port = ThreadLocalRandom.current().nextInt(PORT_RANGE_START, PORT_RANGE_END + 1);
            if (usedPorts.contains(port)) {
                continue;
            }
            if (isPortAvailable(port)) {
                usedPorts.add(port);
                return port;
            }
        }
        throw new WanakuException("Failed to allocate a free gRPC port after " + MAX_PORT_ATTEMPTS + " attempts");
    }

    /**
     * Checks whether a port is available by attempting to bind a server socket to it.
     *
     * @param port the port to check
     * @return {@code true} if the port is available
     */
    private static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Builds the composite key used to identify managed processes.
     *
     * @param catalogName the catalog name
     * @param systemName  the system name
     * @return the key in the form {@code catalogName:systemName}
     */
    private static String buildKey(String catalogName, String systemName) {
        return catalogName + ":" + systemName;
    }

    /**
     * Internal holder for a managed subprocess and its metadata.
     */
    private static class ManagedProcess {
        final Thread thread;
        final int grpcPort;
        final Instant startedAt;
        volatile boolean stopped;

        ManagedProcess(Thread thread, int grpcPort, Instant startedAt) {
            this.thread = thread;
            this.grpcPort = grpcPort;
            this.startedAt = startedAt;
        }
    }
}
