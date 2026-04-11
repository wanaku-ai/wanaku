package ai.wanaku.core.mcp.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SamplingMcpTransportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldInterceptSamplingRequest() throws Exception {
        // Arrange
        McpTransport delegate = mock(McpTransport.class);
        McpSamplingHandler samplingHandler = mock(McpSamplingHandler.class);
        try (SamplingMcpTransport transport = new SamplingMcpTransport(delegate, samplingHandler)) {

            Map<Long, CompletableFuture<JsonNode>> pendingOperations = new HashMap<>();
            Supplier<List<McpRoot>> roots = Collections::emptyList;
            Consumer<JsonNode> logMessageConsumer = node -> {};
            Runnable onToolListUpdate = () -> {};

            McpOperationHandler originalHandler = new McpOperationHandler(
                    (Map) pendingOperations,
                    (Supplier) roots,
                    delegate,
                    (Consumer) logMessageConsumer,
                    onToolListUpdate,
                    () -> {},
                    () -> {},
                    (str) -> {},
                    null);

            // When
            transport.start(originalHandler);

            // Capture the intercepting handler
            ArgumentCaptor<McpOperationHandler> handlerCaptor = ArgumentCaptor.forClass(McpOperationHandler.class);
            verify(delegate).start(handlerCaptor.capture());
            McpOperationHandler interceptingHandler = handlerCaptor.getValue();

            // Simulate a sampling request
            ObjectNode request = mapper.createObjectNode();
            request.put("id", 123L);
            request.put("method", "sampling/createMessage");
            ObjectNode params = request.putObject("params");
            params.put("prompt", "hello");

            ObjectNode result = mapper.createObjectNode();
            result.put("text", "hi");
            when(samplingHandler.handleSampling(any())).thenReturn(CompletableFuture.completedFuture(result));

            // Act
            interceptingHandler.handle(request);

            // Assert
            verify(samplingHandler).handleSampling(any());

            ArgumentCaptor<SamplingMcpTransport.SamplingResponseMessage> responseCaptor =
                    ArgumentCaptor.forClass(SamplingMcpTransport.SamplingResponseMessage.class);
            verify(delegate).executeOperationWithoutResponse(responseCaptor.capture());

            SamplingMcpTransport.SamplingResponseMessage response = responseCaptor.getValue();
            assertThat(response.getId()).isEqualTo(123L);
            assertThat(response.getResult()).isEqualTo(result);
        }
    }
}
