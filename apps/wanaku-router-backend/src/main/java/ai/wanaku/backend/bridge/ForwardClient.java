package ai.wanaku.backend.bridge;

import java.util.List;
import io.quarkus.logging.Log;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpRoot;

/**
 * Pairs an MCP server address with a cached {@link McpClient} connection.
 * <p>
 * This record serves as a lightweight wrapper that associates a remote MCP server's
 * address with its corresponding client instance. It is primarily used by the
 * {@link ForwardRegistry} to maintain active connections to forwarded MCP servers.
 * <p>
 * <b>Thread Safety:</b> This record is immutable and thread-safe. However, the
 * underlying {@link McpClient} may have its own threading considerations.
 * <p>
 * <b>Lifecycle:</b> The client should be properly closed when no longer needed,
 * typically handled by {@link ForwardRegistry#unlink(ai.wanaku.capabilities.sdk.api.types.NameNamespacePair)}.
 *
 * @param address the remote MCP server address (e.g., "http://localhost:8080" or "stdio://path/to/server")
 * @param client  the MCP client used to communicate with the server
 */
public record ForwardClient(String address, McpClient client) implements AutoCloseable {

    /**
     * Creates a new ForwardClient with a fresh MCP client connection (no roots).
     *
     * @param address the remote MCP server address to connect to
     * @return a new ForwardClient instance with an initialized client
     * @throws IllegalArgumentException if the address is invalid or unsupported
     */
    public static ForwardClient newClient(String address) {
        return new ForwardClient(address, ClientUtil.createClient(address));
    }

    /**
     * Creates a new ForwardClient with a fresh MCP client connection and configured roots.
     * <p>
     * Root directories are required by some upstream MCP servers (e.g., the filesystem server)
     * that use the {@code roots/list} capability during initialization.
     *
     * @param address the remote MCP server address to connect to
     * @param roots   the root directories to advertise to the upstream server, or {@code null} for none
     * @return a new ForwardClient instance with an initialized client
     * @throws IllegalArgumentException if the address is invalid or unsupported
     */
    public static ForwardClient newClient(String address, List<McpRoot> roots) {
        return new ForwardClient(address, ClientUtil.createClient(address, roots));
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            Log.error("Failed to close ForwardClient: {}", e.getMessage(), e);
        }
    }
}
