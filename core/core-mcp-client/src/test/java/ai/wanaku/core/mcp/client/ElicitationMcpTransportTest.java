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

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElicitationMcpTransportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldInterceptElicitationRequest() throws Exception {
        // Arrange
        McpTransport delegate = mock(McpTransport.class);
        McpElicitationHandler elicitationHandler = mock(McpElicitationHandler.class);
        try (ElicitationMcpTransport transport = new ElicitationMcpTransport(delegate, elicitationHandler)) {

            McpOperationHandler originalHandler = getOperationHandler(delegate);

            // Start transport — installs the intercepting handler
            transport.start(originalHandler);

            // Capture the intercepting handler registered with the delegate
            ArgumentCaptor<McpOperationHandler> handlerCaptor = ArgumentCaptor.forClass(McpOperationHandler.class);
            verify(delegate).start(handlerCaptor.capture());
            McpOperationHandler interceptingHandler = handlerCaptor.getValue();

            // Build an elicitation/create request
            ObjectNode request = mapper.createObjectNode();
            request.put("id", 42L);
            request.put("method", "elicitation/create");
            ObjectNode params = request.putObject("params");
            params.put("message", "What is your name?");
            ObjectNode schema = params.putObject("requestedSchema");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode nameProp = properties.putObject("name");
            nameProp.put("type", "string");

            // The handler returns a successful result
            ObjectNode result = mapper.createObjectNode();
            result.put("action", "accept");
            ObjectNode content = result.putObject("content");
            content.put("name", "Alice");

            when(elicitationHandler.handleElicitation(any())).thenReturn(CompletableFuture.completedFuture(result));

            // Act — feed the elicitation request to the intercepting handler
            interceptingHandler.handle(request);

            // Assert — handler was invoked
            verify(elicitationHandler).handleElicitation(any());

            // Assert — response was sent back through the delegate transport
            ArgumentCaptor<ElicitationMcpTransport.ElicitationResponseMessage> responseCaptor =
                    ArgumentCaptor.forClass(ElicitationMcpTransport.ElicitationResponseMessage.class);
            verify(delegate).executeOperationWithoutResponse(responseCaptor.capture());

            ElicitationMcpTransport.ElicitationResponseMessage response = responseCaptor.getValue();
            assertThat(response.getId()).isEqualTo(42L);
            assertThat(response.getResult().get("action").asText()).isEqualTo("accept");
            assertThat(response.getResult().get("content").get("name").asText()).isEqualTo("Alice");
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldNotInterceptNonElicitationMessages() throws Exception {
        // Arrange
        McpTransport delegate = mock(McpTransport.class);
        McpElicitationHandler elicitationHandler = mock(McpElicitationHandler.class);
        try (ElicitationMcpTransport transport = new ElicitationMcpTransport(delegate, elicitationHandler)) {

            McpOperationHandler originalHandler = getOperationHandler(delegate);

            transport.start(originalHandler);

            ArgumentCaptor<McpOperationHandler> handlerCaptor = ArgumentCaptor.forClass(McpOperationHandler.class);
            verify(delegate).start(handlerCaptor.capture());
            McpOperationHandler interceptingHandler = handlerCaptor.getValue();

            // Build a sampling request (a different method — should NOT be intercepted)
            ObjectNode request = mapper.createObjectNode();
            request.put("id", 99L);
            request.put("method", "sampling/createMessage");
            request.putObject("params");

            // Act
            interceptingHandler.handle(request);

            // Assert — elicitation handler was NOT called
            verify(elicitationHandler, never()).handleElicitation(any());
        }
    }

    private static @NonNull McpOperationHandler getOperationHandler(McpTransport delegate) {
        Map<Long, CompletableFuture<JsonNode>> pendingOperations = new HashMap<>();
        Supplier<List<McpRoot>> roots = Collections::emptyList;
        Consumer<JsonNode> logMessageConsumer = node -> {};
        Runnable onToolListUpdate = () -> {};

        McpOperationHandler originalHandler = new McpOperationHandler(
                (Map) pendingOperations,
                (Supplier) roots,
                delegate,
                (Consumer) logMessageConsumer,
                onToolListUpdate);
        return originalHandler;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSendErrorResponseWhenHandlerFails() throws Exception {
        // Arrange
        McpTransport delegate = mock(McpTransport.class);
        McpElicitationHandler elicitationHandler = mock(McpElicitationHandler.class);
        try (ElicitationMcpTransport transport = new ElicitationMcpTransport(delegate, elicitationHandler)) {

            McpOperationHandler originalHandler = getMcpOperationHandler(delegate);

            transport.start(originalHandler);

            ArgumentCaptor<McpOperationHandler> handlerCaptor = ArgumentCaptor.forClass(McpOperationHandler.class);
            verify(delegate).start(handlerCaptor.capture());
            McpOperationHandler interceptingHandler = handlerCaptor.getValue();

            // Build a request
            ObjectNode request = mapper.createObjectNode();
            request.put("id", 7L);
            request.put("method", "elicitation/create");
            request.putObject("params").put("message", "confirm?");

            // Handler throws
            when(elicitationHandler.handleElicitation(any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Elicitation not supported")));

            // Act
            interceptingHandler.handle(request);

            // Assert — error response was sent
            ArgumentCaptor<ElicitationMcpTransport.ElicitationErrorMessage> errorCaptor =
                    ArgumentCaptor.forClass(ElicitationMcpTransport.ElicitationErrorMessage.class);
            verify(delegate).executeOperationWithoutResponse(errorCaptor.capture());

            ElicitationMcpTransport.ElicitationErrorMessage error = errorCaptor.getValue();
            assertThat(error.getId()).isEqualTo(7L);
            assertThat(error.getError().get("code").asInt()).isEqualTo(-32603);
            assertThat(error.getError().get("message").asText()).contains("not supported");
        }
    }

    private static @NonNull McpOperationHandler getMcpOperationHandler(McpTransport delegate) {
        McpOperationHandler originalHandler = getOperationHandler(delegate);
        return originalHandler;
    }
}
