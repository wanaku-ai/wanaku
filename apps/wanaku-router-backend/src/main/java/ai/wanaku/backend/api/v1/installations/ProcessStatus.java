package ai.wanaku.backend.api.v1.installations;

/**
 * Represents the runtime status of a managed capability process.
 *
 * <p>This POJO captures whether a process launched through the installations API
 * is currently running, what gRPC port it was allocated, when it started, and its
 * exit code (if it has terminated).
 */
public class ProcessStatus {

    private boolean running;
    private int port;
    private String startedAt;
    private Integer exitCode;

    /**
     * Default constructor required for JSON serialization.
     */
    public ProcessStatus() {}

    /**
     * Constructs a ProcessStatus with all fields.
     *
     * @param running   whether the process is currently alive
     * @param port      the gRPC port allocated to the process
     * @param startedAt the ISO 8601 timestamp when the process was started, or {@code null} if never launched
     * @param exitCode  the process exit code, or {@code null} if still running or never launched
     */
    public ProcessStatus(boolean running, int port, String startedAt, Integer exitCode) {
        this.running = running;
        this.port = port;
        this.startedAt = startedAt;
        this.exitCode = exitCode;
    }

    /**
     * Returns whether the process is currently running.
     *
     * @return {@code true} if the process is alive
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets whether the process is currently running.
     *
     * @param running {@code true} if the process is alive
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Returns the gRPC port allocated to this process.
     *
     * @return the port number, or 0 if no port was allocated
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the gRPC port allocated to this process.
     *
     * @param port the port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Returns the ISO 8601 timestamp when the process was started.
     *
     * @return the start time string, or {@code null} if never launched
     */
    public String getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the ISO 8601 timestamp when the process was started.
     *
     * @param startedAt the start time string
     */
    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Returns the process exit code.
     *
     * @return the exit code, or {@code null} if still running or never launched
     */
    public Integer getExitCode() {
        return exitCode;
    }

    /**
     * Sets the process exit code.
     *
     * @param exitCode the exit code
     */
    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    @Override
    public String toString() {
        return "ProcessStatus{"
                + "running=" + running
                + ", port=" + port
                + ", startedAt='" + startedAt + '\''
                + ", exitCode=" + exitCode
                + '}';
    }
}
