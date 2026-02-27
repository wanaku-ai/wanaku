package ai.wanaku.backend.providers;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.List;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.backend.bridge.ResourceAcquirerBridge;
import ai.wanaku.backend.bridge.ResourceBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import picocli.CommandLine;

/**
 * A provider for resources resolvers
 */
@ApplicationScoped
public class ResourcesProvider {
    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    private ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    @Produces
    ServiceResolver getServiceResolver() {
        return new FirstAvailable(serviceRegistry);
    }

    @Produces
    WanakuBridgeTransport getTransport() {
        return new GrpcTransport();
    }

    @Produces
    ResourceBridge getResourceBridge() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            // TODO
            return new ResourceBridge() {
                @Override
                public ProvisioningReference provision(ResourcePayload payload) {
                    return null;
                }

                @Override
                public List<ResourceContents> read(
                        ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
                    return List.of();
                }

                @Override
                public Uni<ResourceResponse> readAsync(
                        ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
                    return Uni.createFrom().item(new ResourceResponse(List.of()));
                }
            };
        }

        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();

        return new ResourceAcquirerBridge(resolver, transport);
    }
}
