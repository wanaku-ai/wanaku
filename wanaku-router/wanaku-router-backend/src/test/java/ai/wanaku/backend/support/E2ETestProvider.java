package ai.wanaku.backend.support;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopForwardRegistry;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import ai.wanaku.core.mcp.providers.ServiceRegistry;

/**
 * CDI alternative provider for e2e tests that produces real bridges backed by gRPC transport.
 * Activated only when the {@link E2ETestProfile} is active.
 */
@Alternative
@ApplicationScoped
public class E2ETestProvider {
    private static final Logger LOG = Logger.getLogger(E2ETestProvider.class);

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    private ServiceRegistry serviceRegistry;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    @Produces
    ToolsBridge toolsBridge() {
        LOG.info("Creating real ToolsBridge for e2e tests");
        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();
        return new InvokerBridge(resolver, transport);
    }

    @Produces
    ServiceResolver getServiceResolver() {
        LOG.info("Creating real ServiceResolver for e2e tests");
        return new FirstAvailable(serviceRegistry);
    }

    @Produces
    WanakuBridgeTransport getTransport() {
        LOG.info("Creating real GrpcTransport for e2e tests");
        return new GrpcTransport();
    }

    @Produces
    ForwardRegistry forwardRegistry() {
        return new NoopForwardRegistry();
    }
}
