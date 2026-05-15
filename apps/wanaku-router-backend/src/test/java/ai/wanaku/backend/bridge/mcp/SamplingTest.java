package ai.wanaku.backend.bridge.mcp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.smallrye.mutiny.Uni;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SamplingTest {

    private DefaultMcpBridge bridge;
    private Sampling sampling;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bridge = new DefaultMcpBridge();
        sampling = mock(Sampling.class);
        bridge.sampling = sampling;
    }

    @Test
    void shouldHandleSamplingRequest() throws Exception {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("maxTokens", 100);
        params.put("systemPrompt", "You are helpful");
        ArrayNode messages = params.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ObjectNode content = msg.putObject("content");
        content.put("type", "text");
        content.put("text", "hello");

        SamplingRequest.Builder builder = mock(SamplingRequest.Builder.class);
        SamplingRequest request = mock(SamplingRequest.class);
        SamplingResponse response = mock(SamplingResponse.class);

        when(sampling.isSupported()).thenReturn(true);
        when(sampling.requestBuilder()).thenReturn(builder);
        when(builder.setMaxTokens(100L)).thenReturn(builder);
        when(builder.setSystemPrompt("You are helpful")).thenReturn(builder);
        when(builder.addMessage(any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));

        when(response.model()).thenReturn("test-model");
        when(response.stopReason()).thenReturn("stop");
        TextContent textContent = new TextContent("hi there");
        when(response.content()).thenReturn(textContent);

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createSamplingHandler(address).handleSampling(params);
        JsonNode result = future.get(5, TimeUnit.SECONDS);

        // Assert
        verify(sampling).requestBuilder();
        verify(builder).setMaxTokens(100L);
        verify(builder).setSystemPrompt("You are helpful");
        verify(builder).build();

        assertThat(result.get("model").asText()).isEqualTo("test-model");
        assertThat(result.get("stopReason").asText()).isEqualTo("stop");
        assertThat(result.get("content").get("text").asText()).isEqualTo("hi there");
    }
}
