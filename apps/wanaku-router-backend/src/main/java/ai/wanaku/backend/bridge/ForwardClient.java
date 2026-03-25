package ai.wanaku.backend.bridge;

import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;

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
public record ForwardClient(String address, McpClient client) {

    /**
     * Creates a new ForwardClient with a fresh MCP client connection.
     * <p>
     * This factory method initializes a new {@link McpClient} using {@link ClientUtil#createClient(String)}
     * and wraps it with the provided address.
     *
     * @param address the remote MCP server address to connect to
     * @return a new ForwardClient instance with an initialized client
     * @throws IllegalArgumentException if the address is invalid or unsupported
     */
    public static ForwardClient newClient(String address) {
        return new ForwardClient(address, ClientUtil.createClient(address));
    }
}
