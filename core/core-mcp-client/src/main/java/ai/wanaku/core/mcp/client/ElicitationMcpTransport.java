package ai.wanaku.core.mcp.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;

/**
 * A decorating {@link McpTransport} that intercepts {@code elicitation/create} server-to-client
 * JSON-RPC requests and delegates them to a {@link McpElicitationHandler}.
 *
 * <p>The Wanaku bridge acts as an MCP client towards remote MCP servers. When one of those
 * servers emits an {@code elicitation/create} request (asking the client for user input),
 * this transport intercepts the message before it reaches the default handler, delegates
 * the call to the configured {@link McpElicitationHandler}, and sends the JSON-RPC response
 * back through the delegate transport.</p>
 *
 * <p>All other MCP messages are forwarded unchanged to the original {@link McpOperationHandler}.</p>
 */
public class ElicitationMcpTransport implements McpTransport {

    private static final Logger LOG = LoggerFactory.getLogger(ElicitationMcpTransport.class);
    private static final String ELICITATION_CREATE_METHOD = "elicitation/create";

    static {
        try {
            Class<?> capabilitiesClass = Class.forName("dev.langchain4j.mcp.protocol.McpInitializeParams$Capabilities");
            int registered =
                    McpOperationHandlerReflection.registerObjectMapperMixIn(capabilitiesClass, CapabilitiesMixIn.class);
            if (registered > 0) {
                LOG.debug("Elicitation capability mix-in registered on {} OBJECT_MAPPER instance(s)", registered);
            }
        } catch (ClassNotFoundException e) {
            LOG.warn(
                    "McpInitializeParams$Capabilities class not found; "
                            + "langchain4j-mcp version may have changed. "
                            + "Elicitation capability will not be announced: {}",
                    e.getMessage());
        }
    }

    /**
     * A Mix-In to inject the elicitation capability into the MCP initialization request.
     */
    public interface CapabilitiesMixIn {
        @com.fasterxml.jackson.annotation.JsonProperty("elicitation")
        default Object getElicitation() {
            return java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of());
        }
    }

    private final McpTransport delegate;
    private final McpElicitationHandler elicitationHandler;

    public ElicitationMcpTransport(McpTransport delegate, McpElicitationHandler elicitationHandler) {
        this.delegate = delegate;
        this.elicitationHandler = elicitationHandler;
    }

    @Override
    public void start(McpOperationHandler originalHandler) {
        try {
            Consumer<JsonNode> interceptingHandle = this::handleElicitationRequest;
            McpOperationHandler interceptingHandler = McpOperationHandlerReflection.createInterceptingHandler(
                    originalHandler, this, interceptingHandle, ELICITATION_CREATE_METHOD);
            delegate.start(interceptingHandler);
        } catch (IllegalStateException e) {
            LOG.error(
                    "Failed to wrap McpOperationHandler for elicitation support. "
                            + "This typically indicates an incompatible langchain4j-mcp version: {}",
                    e.getMessage());
            throw new RuntimeException("Failed to wrap McpOperationHandler for elicitation support", e);
        }
    }

    private void handleElicitationRequest(JsonNode node) {
        JsonNode id = node.get("id");
        JsonNode params = node.get("params");

        elicitationHandler
                .handleElicitation(params)
                .thenAccept(result -> {
                    delegate.executeOperationWithoutResponse(new ElicitationResponseMessage(id.asLong(), result));
                })
                .exceptionally(ex -> {
                    LOG.warn("Elicitation request failed: {}", ex.getMessage());
                    ObjectNode errNode = JsonNodeFactory.instance.objectNode();
                    errNode.put("code", -32603);
                    errNode.put("message", ex.getMessage());
                    delegate.executeOperationWithoutResponse(new ElicitationErrorMessage(id.asLong(), errNode));
                    return null;
                });
    }

    // ---- McpTransport delegation ----

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return delegate.initialize(request);
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage operation) {
        return delegate.executeOperationWithResponse(operation);
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        delegate.executeOperationWithoutResponse(operation);
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(dev.langchain4j.mcp.client.McpCallContext context) {
        return delegate.executeOperationWithResponse(context);
    }

    @Override
    public void executeOperationWithoutResponse(dev.langchain4j.mcp.client.McpCallContext context) {
        delegate.executeOperationWithoutResponse(context);
    }

    @Override
    public void checkHealth() {
        delegate.checkHealth();
    }

    @Override
    public void onFailure(Runnable runnable) {
        delegate.onFailure(runnable);
    }

    @Override
    public void close() throws java.io.IOException {
        delegate.close();
    }

    // ---- Inner message types ----

    /**
     * JSON-RPC success response for an {@code elicitation/create} request.
     */
    public static class ElicitationResponseMessage extends McpClientMessage {
        private final JsonNode result;

        public ElicitationResponseMessage(Long id, JsonNode result) {
            super(id, null);
            this.result = result;
        }

        public JsonNode getResult() {
            return result;
        }
    }

    /**
     * JSON-RPC error response for an {@code elicitation/create} request.
     */
    public static class ElicitationErrorMessage extends McpClientMessage {
        private final JsonNode error;

        public ElicitationErrorMessage(Long id, JsonNode error) {
            super(id, null);
            this.error = error;
        }

        public JsonNode getError() {
            return error;
        }
    }
}
