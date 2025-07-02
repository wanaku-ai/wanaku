package ai.wanaku.server.quarkus.common;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.ResourceReference;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import java.util.List;
import java.util.function.BiFunction;
import org.jboss.logging.Logger;

/**
 * Helper class for dealing with resources
 */
public final class ResourceHelper {
    private static final Logger LOG = Logger.getLogger(ResourceHelper.class);

    private ResourceHelper() {}

    /**
     * Expose a resource by applying the given handler to the resource reference and
     * resource manager, which returns a list of resource contents.
     *
     * @param resourceReference The reference to the resource being exposed
     * @param resourceManager   The resource manager instance responsible for managing resources
     * @param handler          A BiFunction that takes ResourceArguments and ResourceReference as input,
     *                          and returns a List of ResourceContents. This handler will be applied to expose the resource.
     */
    public static void expose(ResourceReference resourceReference, ResourceManager resourceManager,
            BiFunction<ResourceManager.ResourceArguments, ResourceReference, List<ResourceContents>> handler)
    {

    }

    /**
     * Expose a resource by applying the given handler to the resource reference and
     * resource manager, which returns a list of resource contents.
     *
     * @param resourceReference The reference to the resource being exposed
     * @param resourceManager   The resource manager instance responsible for managing resources
     * @param handler          A BiFunction that takes ResourceArguments and ResourceReference as input,
     *                          and returns a List of ResourceContents. This handler will be applied to expose the resource.
     */
    public static void expose(ResourceReference resourceReference, ResourceManager resourceManager,
            Namespace namespace, BiFunction<ResourceManager.ResourceArguments, ResourceReference, List<ResourceContents>> handler)
    {
        final ResourceManager.ResourceDefinition resourceDefinition = resourceManager.newResource(resourceReference.getName())
                .setUri(resourceReference.getLocation())
                .setMimeType(resourceReference.getMimeType())
                .setDescription(resourceReference.getDescription())
                .setHandler(args ->
                    new ResourceResponse(handler.apply(args, resourceReference)));

        if (namespace != null) {
            LOG.debugf("Exposing resource %s in namespace %s", resourceReference.getName(), resourceReference.getNamespace());
            resourceDefinition
                    .setServerName(namespace.getPath())
                    .register();
        } else {
            LOG.debugf("Exposing resource %s", resourceReference.getName());
            resourceDefinition
                    .register();
        }
    }
}
