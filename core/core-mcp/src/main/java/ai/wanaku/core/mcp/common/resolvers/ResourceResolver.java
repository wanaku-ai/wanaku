package ai.wanaku.core.mcp.common.resolvers;

import java.util.List;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;

/**
 * Resolver interface for resource capability requests in the MCP system.
 * <p>
 * This resolver processes MCP resource requests and determines which resource provider
 * service should handle them. It supports both resource provisioning (registering
 * resources with their configuration) and resource reading (accessing resource content
 * through the appropriate provider).
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Provisioning resources by loading their properties from remote services</li>
 *   <li>Reading resource contents through the appropriate resource acquirer</li>
 *   <li>Managing the lifecycle of resource capabilities</li>
 * </ul>
 *
 * @see Resolver
 */
public interface ResourceResolver extends Resolver {

    /**
     * Provisions a resource by loading its properties from the remote service capable of handling it.
     * <p>
     * This method registers the resource with the system and retrieves any necessary
     * configuration or metadata from the service provider that will handle resource
     * access requests.
     *
     * @param resourcePayload the resource payload containing the resource reference and provisioning data
     * @throws ServiceNotFoundException if a service capable of handling the resource cannot be found
     */
    void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException;

    /**
     * Reads the contents of a resource.
     * <p>
     * This method retrieves the actual content of a resource by delegating to the
     * appropriate resource acquirer. The content format is specific to the type of
     * resource being read (e.g., text, binary, structured data).
     *
     * @param arguments the resource request arguments containing any parameters needed to read the resource
     * @param mcpResource the resource reference identifying which resource to read
     * @return a list of resource contents, where each entry represents a portion or variant of the resource
     */
    List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
