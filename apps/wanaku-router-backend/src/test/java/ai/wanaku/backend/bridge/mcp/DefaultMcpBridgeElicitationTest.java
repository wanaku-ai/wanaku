package ai.wanaku.backend.bridge.mcp;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.smallrye.mutiny.Uni;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the elicitation support in {@link DefaultMcpBridge}.
 * <p>
 * These tests verify that the bridge correctly bridges {@code elicitation/create}
 * JSON-RPC requests from remote MCP servers to the local Quarkus {@link Elicitation} API,
 * and that the response is correctly serialized back to JSON.
 */
class DefaultMcpBridgeElicitationTest {

    private DefaultMcpBridge bridge;
    private Elicitation elicitation;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bridge = new DefaultMcpBridge();
        elicitation = mock(Elicitation.class);
        bridge.elicitation = elicitation;
    }

    @Test
    void shouldHandleElicitationRequestWithAcceptAction() throws Exception {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "What is your name?");
        ObjectNode requestedSchema = params.putObject("requestedSchema");
        ObjectNode properties = requestedSchema.putObject("properties");
        ObjectNode nameProp = properties.putObject("name");
        nameProp.put("type", "string");

        ElicitationRequest.Builder builder = mock(ElicitationRequest.Builder.class);
        ElicitationRequest request = mock(ElicitationRequest.class);
        ElicitationResponse response = mock(ElicitationResponse.class);
        ElicitationResponse.Content content = mock(ElicitationResponse.Content.class);

        when(elicitation.isSupported()).thenReturn(true);
        when(elicitation.requestBuilder()).thenReturn(builder);
        when(builder.setMessage(anyString())).thenReturn(builder);
        when(builder.addSchemaProperty(eq("name"), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));

        when(response.action()).thenReturn(ElicitationResponse.Action.ACCEPT);
        when(response.content()).thenReturn(content);
        when(content.asMap()).thenReturn(Map.of("name", "Alice"));

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createElicitationHandler(address).handleElicitation(params);
        JsonNode result = future.get(5, TimeUnit.SECONDS);

        // Assert
        verify(elicitation).requestBuilder();
        verify(builder).setMessage("What is your name?");
        verify(builder).addSchemaProperty(eq("name"), any(ElicitationRequest.StringSchema.class));
        verify(builder).build();

        assertThat(result.get("action").asText()).isEqualTo("accept");
        assertThat(result.get("content").get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void shouldHandleElicitationRequestWithDeclineAction() throws Exception {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Confirm operation?");
        ObjectNode requestedSchema = params.putObject("requestedSchema");
        ObjectNode properties = requestedSchema.putObject("properties");
        ObjectNode confirmProp = properties.putObject("confirmed");
        confirmProp.put("type", "boolean");

        ElicitationRequest.Builder builder = mock(ElicitationRequest.Builder.class);
        ElicitationRequest request = mock(ElicitationRequest.class);
        ElicitationResponse response = mock(ElicitationResponse.class);
        ElicitationResponse.Content content = mock(ElicitationResponse.Content.class);

        when(elicitation.isSupported()).thenReturn(true);
        when(elicitation.requestBuilder()).thenReturn(builder);
        when(builder.setMessage(anyString())).thenReturn(builder);
        when(builder.addSchemaProperty(eq("confirmed"), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));

        when(response.action()).thenReturn(ElicitationResponse.Action.DECLINE);
        when(response.content()).thenReturn(content);
        when(content.asMap()).thenReturn(Map.of());

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createElicitationHandler(address).handleElicitation(params);
        JsonNode result = future.get(5, TimeUnit.SECONDS);

        // Assert
        verify(builder).addSchemaProperty(eq("confirmed"), any(ElicitationRequest.BooleanSchema.class));
        assertThat(result.get("action").asText()).isEqualTo("decline");
    }

    @Test
    void shouldMapNumericTypesToNumberSchema() throws Exception {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Enter a number");
        ObjectNode requestedSchema = params.putObject("requestedSchema");
        ObjectNode properties = requestedSchema.putObject("properties");
        properties.putObject("count").put("type", "integer");
        properties.putObject("ratio").put("type", "number");

        ElicitationRequest.Builder builder = mock(ElicitationRequest.Builder.class);
        ElicitationRequest request = mock(ElicitationRequest.class);
        ElicitationResponse response = mock(ElicitationResponse.class);
        ElicitationResponse.Content content = mock(ElicitationResponse.Content.class);

        when(elicitation.isSupported()).thenReturn(true);
        when(elicitation.requestBuilder()).thenReturn(builder);
        when(builder.setMessage(anyString())).thenReturn(builder);
        when(builder.addSchemaProperty(anyString(), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));
        when(response.action()).thenReturn(ElicitationResponse.Action.ACCEPT);
        when(response.content()).thenReturn(content);
        when(content.asMap()).thenReturn(Map.of());

        // Act
        bridge.createElicitationHandler(address).handleElicitation(params).get(5, TimeUnit.SECONDS);

        // Assert — both integer and number map to NumberSchema (IntegerSchema absent in 1.10.1)
        verify(builder).addSchemaProperty(eq("count"), any(ElicitationRequest.NumberSchema.class));
        verify(builder).addSchemaProperty(eq("ratio"), any(ElicitationRequest.NumberSchema.class));
    }

    @Test
    void shouldRespectRequiredArrayAtSchemaRoot() throws Exception {
        // Arrange — 'name' is required, 'nickname' is optional
        // Per the MCP spec the required list lives at requestedSchema root, NOT inside each property.
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Tell us about yourself");
        ObjectNode requestedSchema = params.putObject("requestedSchema");
        ObjectNode properties = requestedSchema.putObject("properties");
        properties.putObject("name").put("type", "string");
        properties.putObject("nickname").put("type", "string");
        ArrayNode required = requestedSchema.putArray("required");
        required.add("name"); // only 'name' is required

        ElicitationRequest.Builder builder = mock(ElicitationRequest.Builder.class);
        ElicitationRequest request = mock(ElicitationRequest.class);
        ElicitationResponse response = mock(ElicitationResponse.class);
        ElicitationResponse.Content content = mock(ElicitationResponse.Content.class);

        when(elicitation.isSupported()).thenReturn(true);
        when(elicitation.requestBuilder()).thenReturn(builder);
        when(builder.setMessage(anyString())).thenReturn(builder);
        when(builder.addSchemaProperty(anyString(), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));
        when(response.action()).thenReturn(ElicitationResponse.Action.ACCEPT);
        when(response.content()).thenReturn(content);
        when(content.asMap()).thenReturn(Map.of("name", "Alice"));

        // Act
        bridge.createElicitationHandler(address).handleElicitation(params).get(5, TimeUnit.SECONDS);

        // Assert — 'name' required=true, 'nickname' required=false
        verify(builder)
                .addSchemaProperty(
                        eq("name"), argThat(s -> s instanceof ElicitationRequest.StringSchema ss && ss.required()));
        verify(builder)
                .addSchemaProperty(
                        eq("nickname"),
                        argThat(s -> s instanceof ElicitationRequest.StringSchema ss && !ss.required()));
    }

    @Test
    void shouldHandleEnumSchema() throws Exception {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Pick a color");
        ObjectNode requestedSchema = params.putObject("requestedSchema");
        ObjectNode properties = requestedSchema.putObject("properties");
        ObjectNode colorProp = properties.putObject("color");
        ArrayNode enums = colorProp.putArray("enum");
        enums.add("red").add("green").add("blue");

        ElicitationRequest.Builder builder = mock(ElicitationRequest.Builder.class);
        ElicitationRequest request = mock(ElicitationRequest.class);
        ElicitationResponse response = mock(ElicitationResponse.class);
        ElicitationResponse.Content content = mock(ElicitationResponse.Content.class);

        when(elicitation.isSupported()).thenReturn(true);
        when(elicitation.requestBuilder()).thenReturn(builder);
        when(builder.setMessage(anyString())).thenReturn(builder);
        when(builder.addSchemaProperty(eq("color"), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.send()).thenReturn(Uni.createFrom().item(response));
        when(response.action()).thenReturn(ElicitationResponse.Action.ACCEPT);
        when(response.content()).thenReturn(content);
        when(content.asMap()).thenReturn(Map.of("color", "red"));

        // Act
        bridge.createElicitationHandler(address).handleElicitation(params).get(5, TimeUnit.SECONDS);

        // Assert
        verify(builder).addSchemaProperty(eq("color"), any(ElicitationRequest.EnumSchema.class));
    }

    @Test
    void shouldFailForUrlMode() {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("mode", "url");
        params.put("message", "Click here");

        when(elicitation.isSupported()).thenReturn(true);

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createElicitationHandler(address).handleElicitation(params);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("URL mode elicitation is not supported yet");
    }

    @Test
    void shouldFailWhenElicitationIsNotSupported() {
        // Arrange
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Are you there?");

        when(elicitation.isSupported()).thenReturn(false);

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createElicitationHandler(address).handleElicitation(params);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Elicitation not supported");
    }

    @Test
    void shouldFailWhenNoElicitationIsAvailable() {
        // Arrange
        bridge.elicitation = null; // no injection
        String address = "http://localhost:8080";
        ObjectNode params = mapper.createObjectNode();
        params.put("message", "Confirm?");

        // Act
        CompletableFuture<JsonNode> future =
                bridge.createElicitationHandler(address).handleElicitation(params);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Elicitation not supported");
    }
}
