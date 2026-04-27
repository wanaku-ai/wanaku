package ai.wanaku.core.mcp.client;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;

/**
 * A decorating {@link McpTransport} that intercepts {@code elicitation/create} server-to-client
 * JSON-RPC requests and delegates them to a {@link McpElicitationHandler}.
 * <p>
 * The Wanaku bridge acts as an MCP client towards remote MCP servers. When one of those
 * servers emits an {@code elicitation/create} request (asking the client for user input),
 * this transport intercepts the message before it reaches the default handler, delegates
 * the call to the configured {@link McpElicitationHandler}, and sends the JSON-RPC response
 * back through the delegate transport.
 * <p>
 * All other MCP messages are forwarded unchanged to the original {@link McpOperationHandler}.
 */
public class ElicitationMcpTransport implements McpTransport {

    private static final String ELICITATION_CREATE_METHOD = "elicitation/create";

    static {
        // MCP Elicitation capability announcement hack.
        // Since langchain4j-mcp doesn't support elicitation yet, we use a Mix-In to inject
        // the capability into the initialization request serialized by the client.
        try {
            Class<?> capabilitiesClass = Class.forName("dev.langchain4j.mcp.protocol.McpInitializeParams$Capabilities");
            registerMixIn(capabilitiesClass);
        } catch (Exception e) {
            // Log or ignore if classes are not found
            System.err.println("Failed to register elicitation capability mix-in: " + e.getMessage());
        }
    }

    private static void registerMixIn(Class<?> capabilitiesClass) throws Exception {
        String[] classesToPatch = {
            "dev.langchain4j.mcp.client.DefaultMcpClient",
            "dev.langchain4j.mcp.client.transport.http.HttpMcpTransport",
            "dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport"
        };

        for (String className : classesToPatch) {
            try {
                Class<?> clazz = Class.forName(className);
                Field field = clazz.getDeclaredField("OBJECT_MAPPER");
                field.setAccessible(true);
                ObjectMapper mapper = (ObjectMapper) field.get(null);
                mapper.addMixIn(capabilitiesClass, CapabilitiesMixIn.class);
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                // Ignore if specific transport is not on the classpath
            }
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
            McpOperationHandler interceptingHandler = createInterceptingHandler(originalHandler);
            delegate.start(interceptingHandler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap McpOperationHandler for elicitation support", e);
        }
    }

    @SuppressWarnings("unchecked")
    private McpOperationHandler createInterceptingHandler(McpOperationHandler original) throws Exception {
        // Extract fields from the original handler to recreate it with the same wiring,
        // pointing the embedded transport reference at this wrapper so that responses
        // sent from within the new handler travel through this transport.
        Map<Long, CompletableFuture<JsonNode>> pendingOperations = getFieldValue(original, "pendingOperations");
        Supplier<?> roots = getFieldValue(original, "roots");
        Consumer<?> logMessageConsumer = getFieldValue(original, "logMessageConsumer");
        Runnable onToolListUpdate = getFieldValue(original, "onToolListUpdate");
        Runnable onResourceListUpdate = getFieldValue(original, "onResourceListUpdate");
        Runnable onPromptListUpdate = getFieldValue(original, "onPromptListUpdate");
        Consumer<String> onResourceUpdate = getFieldValue(original, "onResourceUpdate");
        dev.langchain4j.mcp.client.progress.McpProgressHandler progressHandler =
                getFieldValue(original, "progressHandler");

        return new McpOperationHandler(
                pendingOperations,
                (Supplier) roots,
                this,
                (Consumer) logMessageConsumer,
                onToolListUpdate,
                onResourceListUpdate,
                onPromptListUpdate,
                onResourceUpdate,
                progressHandler) {
            @Override
            public void handle(JsonNode node) {
                if (node.has("method")
                        && ELICITATION_CREATE_METHOD.equals(node.get("method").asText())) {
                    handleElicitationRequest(node);
                } else {
                    original.handle(node);
                }
            }
        };
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
                    ObjectNode errNode = JsonNodeFactory.instance.objectNode();
                    errNode.put("code", -32603);
                    errNode.put("message", ex.getMessage());
                    delegate.executeOperationWithoutResponse(new ElicitationErrorMessage(id.asLong(), errNode));
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
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
