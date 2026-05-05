package ai.wanaku.backend.bridge.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;

import ai.wanaku.backend.bridge.ForwardClient;
import ai.wanaku.backend.bridge.McpBridge;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.mcp.client.McpElicitationHandler;
import ai.wanaku.core.mcp.client.McpSamplingHandler;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Default implementation of {@link McpBridge} that interacts with remote MCP servers
 * via the langchain4j MCP client.
 */
@ApplicationScoped
public class DefaultMcpBridge implements McpBridge {
    private static final Logger LOG = Logger.getLogger(DefaultMcpBridge.class);

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, Sampling> activeSamplings = new ConcurrentHashMap<>();
    private final Map<String, Elicitation> activeElicitations = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    Sampling sampling;
    Elicitation elicitation;

    @Inject
    Instance<Sampling> samplingInstance;

    @Inject
    Instance<Elicitation> elicitationInstance;

    // ---- Tools ----

    @Override
    public List<RemoteToolReference> listTools(ForwardClient forwardClient) throws ServiceUnavailableException {
        try {
            List<RemoteToolReference> references = new ArrayList<>();
            List<ToolSpecification> toolSpecifications = forwardClient.client().listTools();
            for (ToolSpecification toolSpecification : toolSpecifications) {
                RemoteToolReference toolReference = createRemoteToolReference(toolSpecification);
                references.add(toolReference);
            }

            return references;
        } catch (Exception e) {
            throw ServiceUnavailableException.forName(forwardClient.address());
        }
    }

    @Override
    public Uni<ToolResponse> executeTool(
            ForwardClient forwardClient, ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        return Uni.createFrom()
                .item(() -> doExecuteTool(forwardClient, toolArguments, toolReference))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private ToolResponse doExecuteTool(
            ForwardClient forwardClient, ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        LOG.infof("Calling tool on behalf of connection %s", toolArguments.connection().id());

        activeSamplings.put(forwardClient.address(), toolArguments.sampling());
        activeElicitations.put(forwardClient.address(), toolArguments.elicitation());

        ReentrantLock lock = locks.computeIfAbsent(forwardClient.address(), k -> new ReentrantLock());
        try {
            lock.lock();
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolReference.getName())
                    .arguments(serializeArguments(toolArguments.args()))
                    .build();

            ToolExecutionResult result = forwardClient.client().executeTool(request);
            if (result.isError()) {
                return ToolResponse.error(result.resultText());
            }

            return ToolResponse.success(result.resultText());
        } catch (Exception e) {
            LOG.errorf(e, "Unable to remote tool: %s (connection: %s)", e.getMessage(), toolArguments.connection().id());
            return ToolResponse.error(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // ---- Resources ----

    @Override
    public List<ResourceReference> listResources(ForwardClient forwardClient) throws ServiceUnavailableException {
        try {
            List<McpResource> resourceRefs = forwardClient.client().listResources();
            return resourceRefs.stream().map(DefaultMcpBridge::remoteToLocal).collect(Collectors.toList());
        } catch (Exception e) {
            throw ServiceUnavailableException.forName(forwardClient.address());
        }
    }

    @Override
    public Uni<ResourceResponse> read(
            ForwardClient forwardClient, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        activeSamplings.put(forwardClient.address(), arguments.sampling());
        activeElicitations.put(forwardClient.address(), arguments.elicitation());
        return Uni.createFrom()
                .item(() -> doRead(forwardClient, mcpResource))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private ResourceResponse doRead(ForwardClient forwardClient, ResourceReference mcpResource) {
        try {
            McpReadResourceResult resourceResponse = forwardClient.client().readResource(mcpResource.getLocation());

            List<McpResourceContents> contents = resourceResponse.contents();
            TextResourceContents textResourceContents = TextResourceContents.create(
                    mcpResource.getLocation(), contents.getFirst().toString());

            return new ResourceResponse(List.of(textResourceContents));
        } catch (Exception e) {
            throw new WanakuException(e);
        }
    }

    // ---- Sampling / Elicitation handlers ----

    @Override
    public McpSamplingHandler createSamplingHandler(String address) {
        return params -> handleSampling(address, params);
    }

    @Override
    public McpElicitationHandler createElicitationHandler(String address) {
        return params -> handleElicitation(address, params);
    }

    // ---- Sampling ----

    private CompletableFuture<JsonNode> handleSampling(String address, JsonNode params) {
        Sampling s = resolveSampling(address);
        if (s == null || !s.isSupported()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Sampling not supported or no active connection"));
        }

        IllegalArgumentException validationError = validateSamplingContent(params);
        if (validationError != null) {
            return CompletableFuture.failedFuture(validationError);
        }

        try {
            SamplingRequest.Builder builder = buildSamplingRequest(s, params);
            return builder.build().send().subscribeAsCompletionStage().thenApply(this::toSamplingResult);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Sampling resolveSampling(String address) {
        Sampling s = activeSamplings.get(address);
        if (s != null) {
            return s;
        }
        return defaultSampling();
    }

    private IllegalArgumentException validateSamplingContent(JsonNode params) {
        if (params == null || !params.has("messages")) {
            return null;
        }

        for (JsonNode messageNode : params.get("messages")) {
            if (messageNode == null || messageNode.isNull()) {
                continue;
            }
            JsonNode contentNode = messageNode.get("content");
            if (contentNode == null || contentNode.isNull()) {
                continue;
            }

            IllegalArgumentException error = validateContentNode(contentNode);
            if (error != null) {
                return error;
            }
        }

        return null;
    }

    private IllegalArgumentException validateContentNode(JsonNode contentNode) {
        if (contentNode.isArray()) {
            return validateArrayContent(contentNode);
        }
        return validateObjectContent(contentNode);
    }

    private IllegalArgumentException validateArrayContent(JsonNode contentNode) {
        if (contentNode.size() != 1) {
            String error = "Sampling currently supports only a single text content part per message; "
                    + "received " + contentNode.size() + " parts: " + contentNode;
            LOG.warn(error);
            return new IllegalArgumentException(error);
        }
        JsonNode part = contentNode.get(0);
        String type = part.path("type").asText(null);
        if (!"text".equals(type)) {
            String error = "Sampling currently supports only text content parts; "
                    + "received non-text content: " + part;
            LOG.warn(error);
            return new IllegalArgumentException(error);
        }
        return null;
    }

    private IllegalArgumentException validateObjectContent(JsonNode contentNode) {
        String type = contentNode.path("type").asText(null);
        if (type != null && !"text".equals(type)) {
            String error = "Sampling currently supports only text content; "
                    + "received non-text content: " + contentNode;
            LOG.warn(error);
            return new IllegalArgumentException(error);
        }
        return null;
    }

    private SamplingRequest.Builder buildSamplingRequest(Sampling s, JsonNode params) {
        SamplingRequest.Builder builder = s.requestBuilder();

        if (params.has("maxTokens")) {
            builder.setMaxTokens(params.get("maxTokens").asLong());
        }
        if (params.has("systemPrompt")) {
            builder.setSystemPrompt(params.get("systemPrompt").asText());
        }
        if (params.has("temperature")) {
            builder.setTemperature(new BigDecimal(params.get("temperature").asText()));
        }

        if (params.has("messages") && params.get("messages").isArray()) {
            for (JsonNode msgNode : params.get("messages")) {
                addSamplingMessage(builder, msgNode);
            }
        }

        return builder;
    }

    private void addSamplingMessage(SamplingRequest.Builder builder, JsonNode msgNode) {
        String role = msgNode.has("role") ? msgNode.get("role").asText() : "user";
        JsonNode contentNode = msgNode.get("content");
        if (contentNode == null) {
            return;
        }
        if (contentNode.isObject() && "text".equals(contentNode.path("type").asText("text"))) {
            Content textContent = new TextContent(contentNode.path("text").asText(""));
            if ("assistant".equals(role)) {
                builder.addMessage(SamplingMessage.withAssistantRole(textContent));
            } else {
                builder.addMessage(SamplingMessage.withUserRole(textContent));
            }
        }
    }

    private ObjectNode toSamplingResult(io.quarkiverse.mcp.server.SamplingResponse resp) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("model", resp.model());
        if (resp.role() != null) {
            result.put("role", resp.role().toString().toLowerCase());
        }
        if (resp.stopReason() != null) {
            result.put("stopReason", resp.stopReason());
        }
        if (resp.content() != null && "text".equals(resp.content().getType())) {
            ObjectNode contentObj = result.putObject("content");
            contentObj.put("type", "text");
            contentObj.put("text", resp.content().asText().text());
        }
        return result;
    }

    private Sampling defaultSampling() {
        if (sampling != null) {
            return sampling;
        }
        if (samplingInstance != null && samplingInstance.isResolvable()) {
            return samplingInstance.get();
        }
        return null;
    }

    // ---- Elicitation ----

    private CompletableFuture<JsonNode> handleElicitation(String address, JsonNode params) {
        Elicitation e = resolveElicitation(address);
        if (e == null || !e.isSupported()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Elicitation not supported or no active connection"));
        }

        String mode = params.path("mode").asText("form");
        if ("url".equals(mode)) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("URL mode elicitation is not supported yet"));
        }

        try {
            ElicitationRequest.Builder builder = buildElicitationRequest(e, params);
            return builder.build().send().subscribeAsCompletionStage().thenApply(this::toElicitationResult);
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private Elicitation resolveElicitation(String address) {
        Elicitation e = activeElicitations.get(address);
        if (e != null) {
            return e;
        }
        return defaultElicitation();
    }

    private ElicitationRequest.Builder buildElicitationRequest(Elicitation e, JsonNode params) {
        ElicitationRequest.Builder builder = e.requestBuilder();

        builder.setMessage(params.has("message") ? params.get("message").asText() : "");

        JsonNode requestedSchema = params.path("requestedSchema");
        Set<String> requiredProps = collectRequiredProperties(requestedSchema);

        addSchemaProperties(builder, requestedSchema.path("properties"), requiredProps);

        return builder;
    }

    private Set<String> collectRequiredProperties(JsonNode requestedSchema) {
        Set<String> requiredProps = new HashSet<>();
        JsonNode requiredArray = requestedSchema.path("required");
        if (requiredArray.isArray()) {
            requiredArray.forEach(n -> requiredProps.add(n.asText()));
        }
        return requiredProps;
    }

    private void addSchemaProperties(
            ElicitationRequest.Builder builder, JsonNode schemaNode, Set<String> requiredProps) {
        if (!schemaNode.isObject()) {
            return;
        }

        schemaNode.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode propNode = entry.getValue();
            boolean required = requiredProps.contains(key);
            ElicitationRequest.PrimitiveSchema schema = resolvePrimitiveSchema(propNode, required);
            builder.addSchemaProperty(key, schema);
        });
    }

    private ElicitationRequest.PrimitiveSchema resolvePrimitiveSchema(JsonNode propNode, boolean required) {
        if (propNode.has("enum")) {
            List<String> enumValues = new ArrayList<>();
            propNode.get("enum").forEach(n -> enumValues.add(n.asText()));
            return new ElicitationRequest.EnumSchema(enumValues, required);
        }

        String type = propNode.path("type").asText("string");
        return switch (type) {
            case "boolean" -> new ElicitationRequest.BooleanSchema(required);
            case "integer", "number" -> new ElicitationRequest.NumberSchema(required);
            default -> new ElicitationRequest.StringSchema(required);
        };
    }

    private ObjectNode toElicitationResult(io.quarkiverse.mcp.server.ElicitationResponse resp) {
        ObjectNode result = objectMapper.createObjectNode();
        if (resp.action() != null) {
            result.put("action", resp.action().toString().toLowerCase());
        }
        if (resp.content() != null) {
            ObjectNode contentObj = result.putObject("content");
            resp.content().asMap().forEach((k, v) -> putContentValue(contentObj, k, v));
        }
        return result;
    }

    private void putContentValue(ObjectNode contentObj, String key, Object v) {
        if (v instanceof Boolean b) {
            contentObj.put(key, b);
        } else if (v instanceof Integer i) {
            contentObj.put(key, i);
        } else if (v instanceof Number n) {
            contentObj.put(key, n.doubleValue());
        } else if (v != null) {
            contentObj.put(key, v.toString());
        }
    }

    private Elicitation defaultElicitation() {
        if (elicitation != null) {
            return elicitation;
        }
        if (elicitationInstance != null && elicitationInstance.isResolvable()) {
            return elicitationInstance.get();
        }
        return null;
    }

    // ---- Connection lifecycle ----

    @Override
    public void removeConnection(String address) {
        activeSamplings.remove(address);
        activeElicitations.remove(address);
        LOG.infof("Removed active sampling and elicitation entries for address %s", address);
    }

    // ---- General helpers ----

    private String serializeArguments(Map<String, Object> arguments) {
        JsonObject content = new JsonObject();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            content.put(entry.getKey(), entry.getValue());
        }
        return content.toString();
    }

    private static RemoteToolReference createRemoteToolReference(ToolSpecification toolSpecification) {
        RemoteToolReference toolReference = new RemoteToolReference();

        toolReference.setName(toolSpecification.name());
        toolReference.setDescription(toolSpecification.description());
        toolReference.setType("mcp-remote-tool");

        InputSchema inputSchema = new InputSchema();

        JsonObjectSchema parameters = toolSpecification.parameters();

        Map<String, JsonSchemaElement> properties = parameters.properties();
        for (Map.Entry<String, JsonSchemaElement> entry : properties.entrySet()) {
            createProperties(entry, properties, inputSchema);
        }

        toolReference.setInputSchema(inputSchema);
        return toolReference;
    }

    private static void createProperties(
            Map.Entry<String, JsonSchemaElement> entry,
            Map<String, JsonSchemaElement> properties,
            InputSchema inputSchema) {
        String key = entry.getKey();

        JsonSchemaElement jsonSchemaElement = properties.get(key);
        if (jsonSchemaElement instanceof JsonStringSchema stringSchema) {
            createStringProperty(stringSchema, inputSchema, key);
        }
    }

    private static void createStringProperty(JsonStringSchema stringSchema, InputSchema inputSchema, String key) {
        String description = stringSchema.description();

        Property property = new Property();
        property.setDescription(description);
        property.setType("string");

        inputSchema.getProperties().put(key, property);
    }

    private static ResourceReference remoteToLocal(McpResource remoteRef) {
        ResourceReference ref = new ResourceReference();
        ref.setType("mcp-remote-resource");
        ref.setLocation(remoteRef.uri());
        ref.setName(remoteRef.name());
        ref.setDescription(remoteRef.description());
        ref.setMimeType(remoteRef.mimeType());
        return ref;
    }
}
