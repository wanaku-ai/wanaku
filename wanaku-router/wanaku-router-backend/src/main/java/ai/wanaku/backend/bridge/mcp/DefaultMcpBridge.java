package ai.wanaku.backend.bridge.mcp;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.json.JsonObject;
import ai.wanaku.backend.bridge.ForwardClient;
import ai.wanaku.backend.bridge.McpBridge;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
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
    public ToolResponse executeTool(
            ForwardClient forwardClient, ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        LOG.infof(
                "Calling tool on behalf of connection %s",
                toolArguments.connection().id());

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
            LOG.errorf(
                    e,
                    "Unable to remote tool: %s (connection: %s)",
                    e.getMessage(),
                    toolArguments.connection().id());
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
    public List<ResourceContents> read(
            ForwardClient forwardClient, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        try {
            McpReadResourceResult resourceResponse = forwardClient.client().readResource(mcpResource.getLocation());

            List<McpResourceContents> contents = resourceResponse.contents();
            TextResourceContents textResourceContents = TextResourceContents.create(
                    mcpResource.getLocation(), contents.getFirst().toString());

            return List.of(textResourceContents);
        } catch (Exception e) {
            throw new WanakuException(e);
        }
    }

    // ---- Private helpers ----

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
