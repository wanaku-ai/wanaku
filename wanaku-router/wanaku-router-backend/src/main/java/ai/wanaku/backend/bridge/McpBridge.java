package ai.wanaku.backend.bridge;

import java.util.List;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

/**
 * Bridge interface for interacting with remote MCP servers via the langchain4j MCP client.
 * <p>
 * Provides operations for listing and invoking tools, and for listing and reading resources
 * on remote MCP servers.
 */
public interface McpBridge {

    List<RemoteToolReference> listTools(ForwardClient forwardClient) throws ServiceUnavailableException;

    ToolResponse executeTool(
            ForwardClient forwardClient, ToolManager.ToolArguments toolArguments, CallableReference toolReference);

    List<ResourceReference> listResources(ForwardClient forwardClient) throws ServiceUnavailableException;

    List<ResourceContents> read(
            ForwardClient forwardClient, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
