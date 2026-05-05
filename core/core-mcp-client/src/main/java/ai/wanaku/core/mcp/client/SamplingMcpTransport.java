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
import dev.langchain4j.mcp.protocol.McpClientResponse;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;

public class SamplingMcpTransport implements McpTransport {

    private static final Logger LOG = LoggerFactory.getLogger(SamplingMcpTransport.class);
    private static final String SAMPLING_CREATE_METHOD = "sampling/createMessage";

    private final McpTransport delegate;
    private final McpSamplingHandler samplingHandler;

    public SamplingMcpTransport(McpTransport delegate, McpSamplingHandler samplingHandler) {
        this.delegate = delegate;
        this.samplingHandler = samplingHandler;
    }

    @Override
    public void start(McpOperationHandler originalHandler) {
        try {
            Consumer<JsonNode> interceptingHandle = this::handleSamplingRequest;
            McpOperationHandler interceptingHandler = McpOperationHandlerReflection.createInterceptingHandler(
                    originalHandler, this, interceptingHandle, SAMPLING_CREATE_METHOD);
            delegate.start(interceptingHandler);
        } catch (IllegalStateException e) {
            LOG.error(
                    "Failed to wrap McpOperationHandler for sampling support. "
                            + "This typically indicates an incompatible langchain4j-mcp version: {}",
                    e.getMessage());
            throw new RuntimeException("Failed to wrap McpOperationHandler for sampling support", e);
        }
    }

    private void handleSamplingRequest(JsonNode node) {
        JsonNode id = node.get("id");
        JsonNode params = node.get("params");
        samplingHandler
                .handleSampling(params)
                .thenAccept(result -> {
                    delegate.executeOperationWithoutResponse(new SamplingResponseMessage(id.asLong(), result));
                })
                .exceptionally(ex -> {
                    LOG.warn("Sampling request failed: {}", ex.getMessage());
                    ObjectNode errNode = JsonNodeFactory.instance.objectNode();
                    errNode.put("code", -32603);
                    errNode.put("message", ex.getMessage());
                    delegate.executeOperationWithoutResponse(new SamplingErrorMessage(id.asLong(), errNode));
                    return null;
                });
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
    public CompletableFuture<JsonNode> executeOperationWithResponse(
            dev.langchain4j.mcp.client.McpCallContext callContext) {
        return delegate.executeOperationWithResponse(callContext);
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        delegate.executeOperationWithoutResponse(operation);
    }

    @Override
    public void executeOperationWithoutResponse(dev.langchain4j.mcp.client.McpCallContext callContext) {
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
