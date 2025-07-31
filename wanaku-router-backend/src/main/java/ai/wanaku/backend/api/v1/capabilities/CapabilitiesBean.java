package ai.wanaku.backend.api.v1.capabilities;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CapabilitiesBean {
    private static final Logger LOG = Logger.getLogger(CapabilitiesBean.class);

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    private ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
        LOG.infof("Using service registry implementation %s", serviceRegistry.getClass().getName());
    }

    public List<ServiceTarget> toolList() {
        return serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);
    }

    public List<ServiceTarget> resourcesList() {
        return serviceRegistry.getEntries(ServiceType.RESOURCE_PROVIDER);
    }

    public Map<String, List<ActivityRecord>> toolsState() {
        List<ServiceTarget> toolsServices = toolList();
        return buildState(toolsServices);
    }

    public Map<String, List<ActivityRecord>> resourcesState() {
        List<ServiceTarget> resourcesServices = resourcesList();
        return buildState(resourcesServices);
    }

    private Map<String, List<ActivityRecord>> buildState(List<ServiceTarget> serviceTargets) {
        Map<String, List<ActivityRecord>> result = new HashMap<>();

        for (ServiceTarget service : serviceTargets) {
            ActivityRecord state = serviceRegistry.getStates(service.getId());

            final List<ActivityRecord> activityRecords = result.computeIfAbsent(service.getService(), k -> new ArrayList<>());
            activityRecords.add(state);
        }

        return result;
    }
}
