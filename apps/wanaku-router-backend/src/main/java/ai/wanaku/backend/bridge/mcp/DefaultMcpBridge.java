package ai.wanaku.backend.bridge.mcp;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
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
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpException;
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
    static final int JSON_RPC_METHOD_NOT_FOUND = -32601;

    private static final Logger LOG = Logger.getLogger(DefaultMcpBridge.class);

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

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
        } catch (McpException e) {
            if (e.errorCode() == JSON_RPC_METHOD_NOT_FOUND) {
                LOG.infof(
                        "Remote MCP server at %s does not support tools/list, continuing without tools",
                        forwardClient.address());
                return List.of();
            }
            throw new ServiceUnavailableException(
                    String.format("Service is not available at %s", forwardClient.address()), e, false);
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    String.format("Service is not available at %s", forwardClient.address()), e, false);
        }
    }

    @Override
    public Uni<McpSchema.CallToolResult> executeTool(
            String address,
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            McpTransportContext transportContext,
            CallableReference toolReference) {
        return Uni.createFrom()
                .item(() -> doExecuteTool(address, callToolRequest, sessionId, toolReference))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private McpSchema.CallToolResult doExecuteTool(
            String address,
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            CallableReference toolReference) {
        LOG.infof("Calling tool on behalf of session %s", sessionId);

        ReentrantLock lock = locks.computeIfAbsent(address, k -> new ReentrantLock());
        try {
            lock.lock();
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolReference.getName())
                    .arguments(serializeArguments(callToolRequest.arguments()))
                    .build();

            try (var mcpClient = ClientUtil.createClient(address)) {
                ToolExecutionResult result = mcpClient.executeTool(request);
                if (result.isError()) {
                    return McpSchema.CallToolResult.builder(
                                    List.of((McpSchema.Content) McpSchema.TextContent.builder(result.resultText())
                                            .build()))
                            .isError(true)
                            .build();
                }

                return McpSchema.CallToolResult.builder(
                                List.of((McpSchema.Content) McpSchema.TextContent.builder(result.resultText())
                                        .build()))
                        .build();
            }
        } catch (Exception e) {
            LOG.errorf(e, "Unable to remote tool: %s (session: %s)", e.getMessage(), sessionId);
            return McpSchema.CallToolResult.builder(List.of((McpSchema.Content)
                            McpSchema.TextContent.builder(e.getMessage()).build()))
                    .isError(true)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ResourceReference> listResources(ForwardClient forwardClient) throws ServiceUnavailableException {
        try {
            List<McpResource> resourceRefs = forwardClient.client().listResources();
            return resourceRefs.stream().map(DefaultMcpBridge::remoteToLocal).collect(Collectors.toList());
        } catch (McpException e) {
            if (e.errorCode() == JSON_RPC_METHOD_NOT_FOUND) {
                LOG.infof(
                        "Remote MCP server at %s does not support resources/list, continuing without resources",
                        forwardClient.address());
                return List.of();
            }
            throw new ServiceUnavailableException(
                    String.format("Service is not available at %s", forwardClient.address()), e, false);
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    String.format("Service is not available at %s", forwardClient.address()), e, false);
        }
    }

    @Override
    public Uni<McpSchema.ReadResourceResult> read(
            ForwardClient forwardClient,
            McpSchema.ReadResourceRequest readRequest,
            String sessionId,
            McpTransportContext transportContext,
            ResourceReference mcpResource) {
        return Uni.createFrom()
                .item(() -> doRead(forwardClient, mcpResource))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private McpSchema.ReadResourceResult doRead(ForwardClient forwardClient, ResourceReference mcpResource) {
        try {
            McpReadResourceResult resourceResponse = forwardClient.client().readResource(mcpResource.getLocation());

            List<McpResourceContents> contents = resourceResponse.contents();
            McpSchema.TextResourceContents textResourceContents = new McpSchema.TextResourceContents(
                    mcpResource.getLocation(),
                    mcpResource.getMimeType(),
                    contents.getFirst().toString());

            return McpSchema.ReadResourceResult.builder(List.of(textResourceContents))
                    .build();
        } catch (Exception e) {
            throw new WanakuException(e);
        }
    }

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
