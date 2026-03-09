package ai.wanaku.backend.bridge;

import java.util.Objects;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.ResourceRequest;

/**
 * A proxy class for acquiring resources via gRPC.
 * <p>
 * This proxy is responsible for provisioning resource configurations and
 * evaluating resource requests by delegating to remote resource providers.
 * <p>
 * This class uses composition to delegate gRPC transport operations to
 * {@link GrpcTransport}, separating business logic from transport concerns.
 * It focuses solely on resource-specific concerns while delegating infrastructure
 * concerns to the transport layer.
 */
public class ResourceAcquirerBridge implements ResourceBridge {
    private static final Logger LOG = Logger.getLogger(ResourceAcquirerBridge.class);
    private static final String EMPTY_ARGUMENT = "";
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = ServiceType.RESOURCE_PROVIDER.asValue();

    private final ProvisionerBridge provisioner;
    private final WanakuBridgeTransport transport;

    static class WanakuResourceContext {
        ResourceManager.ResourceArguments arguments;
        ResourceReference mcpResource;
        ServiceTarget serviceTarget;
        ResourceRequest request;

        static WanakuResourceContext create(
                ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
            WanakuResourceContext wanakuResourceContext = new WanakuResourceContext();

            wanakuResourceContext.arguments = arguments;
            wanakuResourceContext.mcpResource = mcpResource;
            return wanakuResourceContext;
        }
    }

    /**
     * Creates a new ResourceAcquirerBridge with the specified provisioner and transport.
     *
     * @param provisioner the provisioner bridge for service resolution and provisioning
     * @param transport the transport for communication
     */
    public ResourceAcquirerBridge(ProvisionerBridge provisioner, WanakuBridgeTransport transport) {
        this.provisioner = provisioner;
        this.transport = transport;
    }

    @Override
    public Uni<ResourceResponse> readAsync(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        return Uni.createFrom()
                .item(() -> WanakuResourceContext.create(arguments, mcpResource))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .invoke(this::resolveServiceV2)
                .chain(ctx -> transport
                        .acquireResourceAsync(ctx.request, ctx.serviceTarget, arguments, mcpResource)
                        .map(ResourceResponse::new));
    }

    /**
     * Builds a resource request from the resource reference.
     *
     * @param mcpResource the resource reference
     * @return the resource request
     */
    private ResourceRequest buildResourceRequest(ResourceReference mcpResource) {
        return ResourceRequest.newBuilder()
                .setLocation(mcpResource.getLocation())
                .setType(mcpResource.getType())
                .setName(mcpResource.getName())
                .setConfigurationUri(Objects.requireNonNullElse(mcpResource.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsUri(Objects.requireNonNullElse(mcpResource.getSecretsURI(), EMPTY_ARGUMENT))
                .build();
    }

    @Override
    public ProvisioningReference provision(ResourcePayload payload) {
        ResourceReference resourceReference = payload.getPayload();

        LOG.debugf("Provisioning resource: %s (type: %s)", resourceReference.getName(), resourceReference.getType());

        ServiceTarget service = provisioner.resolveService(resourceReference.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);

        return provisioner.provision(
                resourceReference.getName(), payload.getConfigurationData(), payload.getSecretsData(), service);
    }

    private WanakuResourceContext resolveServiceV2(WanakuResourceContext context) {

        context.serviceTarget =
                provisioner.resolveService(context.mcpResource.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);
        context.request = buildResourceRequest(context.mcpResource);

        return context;
    }
}
