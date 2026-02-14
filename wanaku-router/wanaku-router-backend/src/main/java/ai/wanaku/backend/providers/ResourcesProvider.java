package ai.wanaku.backend.providers;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import ai.wanaku.backend.bridge.ResourceAcquirerBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.resolvers.WanakuResourceResolver;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopResourceResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import picocli.CommandLine;

/**
 * A provider for resources resolvers
 */
@ApplicationScoped
public class ResourcesProvider extends AbstractProvider<ResourceResolver> {
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
    @Override
    ResourceResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopResourceResolver();
        }

        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();
        return new WanakuResourceResolver(new ResourceAcquirerBridge(resolver, transport));
    }
}
