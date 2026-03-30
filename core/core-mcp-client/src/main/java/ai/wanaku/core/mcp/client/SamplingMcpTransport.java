package ai.wanaku.core.mcp.client;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientResponse;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;

public class SamplingMcpTransport implements McpTransport {

    private final McpTransport delegate;
    private final McpSamplingHandler samplingHandler;

    public SamplingMcpTransport(McpTransport delegate, McpSamplingHandler samplingHandler) {
        this.delegate = delegate;
        this.samplingHandler = samplingHandler;
    }

    @Override
    public void start(McpOperationHandler originalHandler) {
        try {
            McpOperationHandler interceptingHandler = createInterceptingHandler(originalHandler);
            delegate.start(interceptingHandler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap McpOperationHandler for sampling support", e);
        }
    }

    @SuppressWarnings("unchecked")
    private McpOperationHandler createInterceptingHandler(McpOperationHandler original) throws Exception {
        // Extract fields from the original handler to create a new one with the same state
        Map<Long, CompletableFuture<JsonNode>> pendingOperations = getFieldValue(original, "pendingOperations");
        Supplier<java.util.List<McpRoot>> roots = getFieldValue(original, "roots");
        Consumer<McpLogMessage> logMessageConsumer = getFieldValue(original, "logMessageConsumer");
        Runnable onToolListUpdate = getFieldValue(original, "onToolListUpdate");
        Runnable onResourceListUpdate = getFieldValue(original, "onResourceListUpdate");
        Runnable onPromptListUpdate = getFieldValue(original, "onPromptListUpdate");
        Consumer<String> onResourceUpdate = getFieldValue(original, "onResourceUpdate");
        McpProgressHandler progressHandler = getFieldValue(original, "progressHandler");

        return new McpOperationHandler(
                pendingOperations,
                roots,
                this,
                logMessageConsumer,
                onToolListUpdate,
                onResourceListUpdate,
                onPromptListUpdate,
                onResourceUpdate,
                progressHandler) {
            @Override
            public void handle(JsonNode node) {
                if (node.has("method")
                        && "sampling/createMessage".equals(node.get("method").asText())) {
                    handleSamplingRequest(node);
                } else {
                    original.handle(node);
                }
            }
        };
    }

    private void handleSamplingRequest(JsonNode node) {
        JsonNode id = node.get("id");
        JsonNode params = node.get("params");
        samplingHandler
                .handleSampling(params)
                .thenAccept(result -> {
                    // Send response back
                    delegate.executeOperationWithoutResponse(new SamplingResponseMessage(id.asLong(), result));
                })
                .exceptionally(ex -> {
                    // Send error back
                    ObjectNode errNode = JsonNodeFactory.instance.objectNode();
                    errNode.put("code", -32603);
                    errNode.put("message", ex.getMessage());
                    delegate.executeOperationWithoutResponse(new SamplingErrorMessage(id.asLong(), errNode));
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return delegate.initialize(request);
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage operation) {
        return delegate.executeOperationWithResponse(operation);
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext callContext) {
        return delegate.executeOperationWithResponse(callContext);
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        delegate.executeOperationWithoutResponse(operation);
    }

    @Override
    public void executeOperationWithoutResponse(McpCallContext callContext) {
        delegate.executeOperationWithoutResponse(callContext);
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

    public static class SamplingResponseMessage extends McpClientResponse {
        private final JsonNode result;

        public SamplingResponseMessage(Long id, JsonNode result) {
            super(id);
            this.result = result;
        }

        public JsonNode getResult() {
            return result;
        }
    }

    public static class SamplingErrorMessage extends McpClientResponse {
        private final JsonNode error;

        public SamplingErrorMessage(Long id, JsonNode error) {
            super(id);
            this.error = error;
        }

        public JsonNode getError() {
            return error;
        }
    }
}
