package ai.wanaku.backend.api.v1.capabilities;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.StaleCapability;
import ai.wanaku.core.services.api.FleetStatus;
import ai.wanaku.core.services.api.StaleCapabilityInfo;

@ApplicationScoped
public class CapabilitiesBean {
    private static final Logger LOG = Logger.getLogger(CapabilitiesBean.class);

    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = ServiceType.RESOURCE_PROVIDER.asValue();

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    private ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
        LOG.infof(
                "Using service registry implementation %s",
                serviceRegistry.getClass().getName());
    }

    public List<ServiceTarget> listAllCapabilities() {
        return serviceRegistry.getEntries();
    }

    private List<ServiceTarget> toolList(String labelFilter) {
        List<ServiceTarget> tools = serviceRegistry.getEntries(SERVICE_TYPE_TOOL_INVOKER);
        return filterByLabels(tools, labelFilter);
    }

    public List<ServiceTarget> toolList() {
        return toolList(null);
    }

    public List<ServiceTarget> resourcesList(String labelFilter) {
        List<ServiceTarget> resources = serviceRegistry.getEntries(SERVICE_TYPE_RESOURCE_PROVIDER);
        return filterByLabels(resources, labelFilter);
    }

    public List<ServiceTarget> resourcesList() {
        return resourcesList(null);
    }

    private List<ServiceTarget> filterByLabels(List<ServiceTarget> serviceTargets, String labelFilter) {
        // Label filtering is not supported for capabilities
        return serviceTargets;
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

            final List<ActivityRecord> activityRecords =
                    result.computeIfAbsent(service.getServiceName(), k -> new ArrayList<>());
            activityRecords.add(state);
        }

        return result;
    }

    /**
     * Lists all stale capabilities based on the provided criteria.
     *
     * @param maxAgeSeconds the maximum age in seconds since last seen
     * @param inactiveOnly if true, only return capabilities that are also marked as inactive
     * @return a list of stale capability information
     */
    public List<StaleCapabilityInfo> listStaleCapabilities(long maxAgeSeconds, boolean inactiveOnly) {
        List<StaleCapability> staleCapabilities = serviceRegistry.findStaleCapabilities(maxAgeSeconds, inactiveOnly);

        return staleCapabilities.stream().map(this::toStaleCapabilityInfo).toList();
    }

    /**
     * Cleans up (removes) all stale capabilities based on the provided criteria.
     *
     * @param maxAgeSeconds the maximum age in seconds since last seen
     * @param inactiveOnly if true, only remove capabilities that are also marked as inactive
     * @return a list of removed service targets for event emission
     */
    public List<ServiceTarget> cleanupStaleCapabilities(long maxAgeSeconds, boolean inactiveOnly) {
        List<StaleCapability> staleCapabilities = serviceRegistry.findStaleCapabilities(maxAgeSeconds, inactiveOnly);
        List<ServiceTarget> removedTargets = new ArrayList<>();

        for (StaleCapability stale : staleCapabilities) {
            ServiceTarget serviceTarget = stale.serviceTarget();
            if (serviceRegistry.removeById(serviceTarget.getId())) {
                removedTargets.add(serviceTarget);
                LOG.infof(
                        "Removed stale capability: %s (service: %s)",
                        serviceTarget.getId(), serviceTarget.getServiceName());
            }
        }

        LOG.infof("Stale capability cleanup complete: %d capabilities removed", removedTargets.size());
        return removedTargets;
    }

    /**
     * Computes the aggregated fleet health status across all registered capabilities.
     *
     * @return the fleet status summary
     */
    public FleetStatus getFleetStatus() {
        List<ServiceTarget> allEntries = serviceRegistry.getEntries();
        int total = allEntries.size();
        int healthy = 0;
        int warning = 0;
        int down = 0;

        for (ServiceTarget entry : allEntries) {
            ActivityRecord record = serviceRegistry.getStates(entry.getId());
            if (record == null) {
                warning++;
                continue;
            }

            HealthStatus status = record.getHealthStatus();
            switch (status) {
                case HEALTHY -> healthy++;
                case UNHEALTHY, PENDING -> warning++;
                case DOWN -> down++;
            }
        }

        String overall;
        if (down > 0) {
            overall = "Down";
        } else if (warning > 0) {
            overall = "Warning";
        } else {
            overall = "Healthy";
        }

        return new FleetStatus(total, healthy, warning, down, overall);
    }

    private StaleCapabilityInfo toStaleCapabilityInfo(StaleCapability stale) {
        ServiceTarget target = stale.serviceTarget();
        ActivityRecord activity = stale.activityRecord();

        return new StaleCapabilityInfo(
                target.getId(),
                target.getServiceName(),
                target.getServiceType(),
                target.getHost(),
                target.getPort(),
                activity != null ? activity.getHealthStatus().asValue() : HealthStatus.PENDING.asValue(),
                activity != null ? activity.getLastSeen() : null);
    }
}
