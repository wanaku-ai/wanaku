package ai.wanaku.core.mcp.client;

import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handler for MCP sampling requests.
 */
@FunctionalInterface
public interface McpSamplingHandler {

    /**
     * Handles a sampling/createMessage request.
     *
     * @param params The "params" object from the JSON-RPC request.
     * @return A CompletableFuture that completes with the JSON-RPC "result" object.
     */
    CompletableFuture<JsonNode> handleSampling(JsonNode params);
}
