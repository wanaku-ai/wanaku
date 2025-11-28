package ai.wanaku.backend.api.v1.management.discovery;

import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DiscoveryBean {
    private static final Logger LOG = Logger.getLogger(DiscoveryBean.class);

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    private ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
        LOG.debugf(
                "Using service registry implementation %s",
                serviceRegistry.getClass().getName());
    }

    public ServiceTarget registerService(ServiceTarget target) {
        return serviceRegistry.register(target);
    }

    public void deregisterService(ServiceTarget target) {
        serviceRegistry.deregister(target);
    }

    public void ping(String id) {
        serviceRegistry.ping(id);
    }

    public void updateState(String id, ServiceState state) {
        serviceRegistry.updateLastState(id, state);
    }
}
