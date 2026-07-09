package ai.wanaku.backend.common;

import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

public final class ResourceHelper {
    private static final Logger LOG = Logger.getLogger(ResourceHelper.class);

    private ResourceHelper() {}

    @FunctionalInterface
    public interface ResourceHandler {
        Uni<McpSchema.ReadResourceResult> read(
                McpSchema.ReadResourceRequest request, String sessionId, ResourceReference mcpResource);
    }

    public static void expose(ResourceReference resourceReference, McpSyncServer server, ResourceHandler handler) {
        expose(resourceReference, server, null, handler);
    }

    public static void expose(
            ResourceReference resourceReference, McpSyncServer server, Namespace namespace, ResourceHandler handler) {

        McpSchema.Resource resource = McpSchema.Resource.builder(
                        resourceReference.getLocation(), resourceReference.getName())
                .description(resourceReference.getDescription())
                .mimeType(resourceReference.getMimeType())
                .build();

        McpServerFeatures.SyncResourceSpecification spec =
                new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
                    return handler.read(request, exchange.sessionId(), resourceReference)
                            .await()
                            .indefinitely();
                });

        try {
            if (namespace != null) {
                LOG.debugf(
                        "Exposing resource %s in namespace %s",
                        resourceReference.getName(), resourceReference.getNamespace());
            } else {
                LOG.debugf("Exposing resource %s", resourceReference.getName());
            }
            server.addResource(spec);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw EntityAlreadyExistsException.forName(resourceReference.getName());
            }
            throw e;
        }
    }
}
