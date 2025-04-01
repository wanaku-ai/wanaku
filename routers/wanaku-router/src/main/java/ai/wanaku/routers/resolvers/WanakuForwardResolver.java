package ai.wanaku.routers.resolvers;

import ai.wanaku.api.exceptions.ServiceUnavailableException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.RemoteToolReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.client.ClientUtil;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.ResourceRef;
import dev.langchain4j.mcp.client.ResourceResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class WanakuForwardResolver implements ForwardResolver {

    private ForwardReference reference;
    private ReentrantLock lock = new ReentrantLock();

    public WanakuForwardResolver(ForwardReference reference) {
        this.reference = reference;
    }

    @Override
    public List<ResourceReference> listResources() throws ServiceUnavailableException {
        try (McpClient client = ClientUtil.createClient(reference.getAddress())) {
            List<ResourceRef> resourceRefs = client.listResources();

            return resourceRefs.stream()
                    .map(WanakuForwardResolver::remoteToLocal).collect(Collectors.toList());
        } catch (Exception e) {
            throw ServiceUnavailableException.forName(reference.getAddress());
        }

    }

    private static ResourceReference remoteToLocal(ResourceRef remoteRef) {
        ResourceReference ref = new ResourceReference();
        ref.setType("mcp-remote-resource");
        ref.setLocation(remoteRef.uri());
        ref.setName(remoteRef.name());
        ref.setDescription(remoteRef.description());
        ref.setMimeType(remoteRef.mimeType());
        return ref;
    }

    @Override
    public List<RemoteToolReference> listTools() throws ServiceUnavailableException {
        try (McpClient client = ClientUtil.createClient(reference.getAddress())) {

            List<RemoteToolReference> references = new ArrayList<>();
            List<ToolSpecification> toolSpecifications = client.listTools();
            for (ToolSpecification toolSpecification : toolSpecifications) {
                RemoteToolReference toolReference = createRemoteToolReference(toolSpecification);
                references.add(toolReference);
            }

            return references;
        } catch (Exception e) {
            throw ServiceUnavailableException.forName(reference.getAddress());
        }
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
            Map.Entry<String, JsonSchemaElement> entry, Map<String, JsonSchemaElement> properties, InputSchema inputSchema) {
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

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        try (McpClient client = ClientUtil.createClient(reference.getAddress())) {
            ResourceResponse resourceResponse = client.readResource(mcpResource.getLocation());

            List<dev.langchain4j.mcp.client.ResourceContents> contents = resourceResponse.contents();
            TextResourceContents textResourceContents = TextResourceContents.create(mcpResource.getLocation(),
                    contents.get(0).asText().text());

            return List.of(textResourceContents);
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

    @Override
    public synchronized Tool resolve(CallableReference toolReference) throws ToolNotFoundException {
        return new ForwardTool();
    }

    private class ForwardTool implements Tool {
        @Override
        public ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
            try {
                lock.lock();
                ToolExecutionRequest request = ToolExecutionRequest
                        .builder()
                        .name(toolReference.getName())
                        .arguments(serializeArguments(toolArguments.args()))
                        .build();
                try (McpClient client = ClientUtil.createClient(reference.getAddress())) {
                    String status = client.executeTool(request);
                    return ToolResponse.success(status);
                }
            } catch (Exception e) {
                return ToolResponse.error(e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }
}
