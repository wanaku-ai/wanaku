package ai.wanaku.backend.bridge;

import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;

/**
 * Pairs an MCP server address with a cached {@link McpClient} connection.
 *
 * @param address the remote MCP server address
 * @param client  the MCP client used to communicate with the server
 */
public record ForwardClient(String address, McpClient client) {

    public static ForwardClient newClient(String address) {
        return new ForwardClient(address, ClientUtil.createClient(address));
    }
}
