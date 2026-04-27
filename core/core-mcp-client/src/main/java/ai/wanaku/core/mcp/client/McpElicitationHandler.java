package ai.wanaku.core.mcp.client;

import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handler for MCP elicitation requests.
 * <p>
 * When a remote MCP server sends an {@code elicitation/create} request, this handler
 * is responsible for delegating it to the client-side elicitation infrastructure
 * (e.g., Quarkus MCP {@code Elicitation} API) and returning the structured response.
 */
@FunctionalInterface
public interface McpElicitationHandler {

    /**
     * Handles an {@code elicitation/create} request.
     *
     * @param params The {@code "params"} object from the JSON-RPC request, containing
     *               {@code "message"} and {@code "requestedSchema"}.
     * @return A {@link CompletableFuture} that completes with the JSON-RPC {@code "result"}
     *         object, containing {@code "action"} and {@code "content"} fields.
     */
    CompletableFuture<JsonNode> handleElicitation(JsonNode params);
}
