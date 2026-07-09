package ai.wanaku.backend.bridge;

import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.ResourceRequest;

public class ResourceAcquirerBridge implements ResourceBridge {
    private static final String EMPTY_ARGUMENT = "";
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = ServiceType.RESOURCE_PROVIDER.asValue();

    private final ProvisionerBridge provisioner;
    private final WanakuBridgeTransport transport;

    static class WanakuResourceContext {
        McpSchema.ReadResourceRequest readRequest;
        String sessionId;
        String requestId;
        ResourceReference mcpResource;
        ServiceTarget serviceTarget;
        ResourceRequest request;

        static WanakuResourceContext create(
                McpSchema.ReadResourceRequest readRequest, String sessionId, ResourceReference mcpResource) {
            WanakuResourceContext ctx = new WanakuResourceContext();
            ctx.readRequest = readRequest;
            ctx.sessionId = sessionId;
            ctx.requestId = UUID.randomUUID().toString();
            ctx.mcpResource = mcpResource;
            return ctx;
        }
    }

    public ResourceAcquirerBridge(ProvisionerBridge provisioner, WanakuBridgeTransport transport) {
        this.provisioner = provisioner;
        this.transport = transport;
    }

    @Override
    public Uni<McpSchema.ReadResourceResult> read(
            McpSchema.ReadResourceRequest readRequest,
            String sessionId,
            McpTransportContext transportContext,
            ResourceReference mcpResource) {

        return Uni.createFrom()
                .item(() -> WanakuResourceContext.create(readRequest, sessionId, mcpResource))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .invoke(ctx -> RequestIdContext.setContext(ctx.requestId, ctx.sessionId))
                .invoke(ctx -> RequestIdContext.setResourceName(mcpResource.getName()))
                .invoke(this::resolveService)
                .chain(ctx -> transport
                        .acquireResource(ctx.request, ctx.serviceTarget, readRequest, mcpResource)
                        .map(contents ->
                                McpSchema.ReadResourceResult.builder(contents).build()))
                .onItemOrFailure()
                .invoke((item, failure) -> RequestIdContext.clear());
    }

    private ResourceRequest buildResourceRequest(ResourceReference mcpResource, String requestId) {
        return ResourceRequest.newBuilder()
                .setLocation(mcpResource.getLocation())
                .setType(mcpResource.getType())
                .setName(mcpResource.getName())
                .setConfigurationUri(Objects.requireNonNullElse(mcpResource.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsUri(Objects.requireNonNullElse(mcpResource.getSecretsURI(), EMPTY_ARGUMENT))
                .setRequestId(Objects.requireNonNullElse(requestId, EMPTY_ARGUMENT))
                .build();
    }

    private WanakuResourceContext resolveService(WanakuResourceContext context) {
        context.serviceTarget =
                provisioner.resolveService(context.mcpResource.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);
        context.request = buildResourceRequest(context.mcpResource, context.requestId);
        return context;
    }
}
