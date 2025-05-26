package ai.wanaku.core.persistence.infinispan;

import jakarta.inject.Inject;

import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import java.util.List;
import java.util.Map;

public class InfinispanServiceRegistry implements ServiceRegistry {

    @Inject
    InfinispanToolTargetRepository toolRepository;

    @Inject
    InfinispanResourceTargetRepository resourceTargetRepository;

    @Override
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        if (serviceTarget.getServiceType() == ServiceType.TOOL_INVOKER) {
            // TODO: how about the configs?
            toolRepository.persist(serviceTarget);
        } else {
            // TODO: how about the configs?
            resourceTargetRepository.persist(serviceTarget);
        }

    }

    @Override
    public void deregister(String service, ServiceType serviceType) {
        if (serviceType == ServiceType.TOOL_INVOKER) {
            toolRepository.deleteById(service);
        } else {
            resourceTargetRepository.deleteById(service);
        }
    }

    @Override
    public Service getService(String service) {
        throw new UnsupportedOperationException("The service type must be informed for Infinispan");
    }

    @Override
    public Service getService(String service, ServiceType serviceType) {
        if (serviceType == ServiceType.TOOL_INVOKER) {
            final ServiceTarget target = toolRepository.findById(service);

            return toService(target);
        } else {
            final ServiceTarget target = resourceTargetRepository.findById(service);

            return toService(target);
        }
    }

    @Override
    public void saveState(String service, boolean healthy, String message) {
        // TODO: not yet supported
    }

    @Override
    public List<State> getState(String service, int count) {
        return List.of();
    }

    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        return Map.of();
    }

    @Override
    public void update(String target, String option, String value) {

    }

    private static Service toService(ServiceTarget entity) {
        Service model = new Service();

        Configurations configurations = new Configurations();

        // TODO
        // configurations.setConfigurations(entity.getConfigurations());
        model.setConfigurations(configurations);

        model.setTarget(entity.toAddress());
        return model;
    }

}
