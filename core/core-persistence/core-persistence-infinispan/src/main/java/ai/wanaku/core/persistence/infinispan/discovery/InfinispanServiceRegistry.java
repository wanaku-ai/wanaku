package ai.wanaku.core.persistence.infinispan.discovery;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

public class InfinispanServiceRegistry implements ServiceRegistry {
    private static final Logger LOG = Logger.getLogger(InfinispanServiceRegistry.class);

    private final InfinispanToolTargetRepository toolRepository;
    private final InfinispanResourceTargetRepository resourceTargetRepository;
    private final InfinispanServiceRecordRepository activityRecordRepository;

    private int maxStateCount;

    public InfinispanServiceRegistry(
            InfinispanResourceTargetRepository resourceTargetRepository,
            InfinispanToolTargetRepository toolRepository,
            InfinispanServiceRecordRepository activityRecordRepository) {
        this.resourceTargetRepository = resourceTargetRepository;
        this.toolRepository = toolRepository;
        this.activityRecordRepository = activityRecordRepository;
    }

    private static void updatePing(ActivityRecord e) {
        e.setLastSeen(Instant.now());
        e.setActive(true);
    }

    @Override
    public ServiceTarget register(ServiceTarget serviceTarget) {
        if (serviceTarget.getServiceType() == ServiceType.TOOL_INVOKER) {
            return toolRepository.persist(serviceTarget);
        } else {
            return resourceTargetRepository.persist(serviceTarget);
        }
    }

    @Override
    public void deregister(ServiceTarget serviceTarget) {
        if (serviceTarget.getServiceType() == ServiceType.TOOL_INVOKER) {
            toolRepository.deleteById(serviceTarget.getId());
        } else {
            resourceTargetRepository.deleteById(serviceTarget.getId());
        }

        activityRecordRepository.update(serviceTarget.getId(), a -> applyDeregistration(serviceTarget.getId(), a));
    }

    private void applyDeregistration(String id, ActivityRecord activityRecord) {
        activityRecord.setLastSeen(Instant.now());
        activityRecord.setActive(false);
        updateLastState(id, ServiceState.newInactive());
    }

    @Override
    public ServiceTarget getServiceByName(String serviceName, ServiceType serviceType) {
        if (serviceType == ServiceType.TOOL_INVOKER) {
            final List<ServiceTarget> serviceTargets = toolRepository.listAll();
            return findService(serviceName, serviceTargets);
        }

        final List<ServiceTarget> serviceTargets = resourceTargetRepository.listAll();
        return findService(serviceName, serviceTargets);
    }

    private static ServiceTarget findService(String serviceId, List<ServiceTarget> serviceTargets) {
        final Optional<ServiceTarget> first = serviceTargets.stream()
                .filter(s -> s.getService().equals(serviceId))
                .findFirst();

        return first.orElse(null);
    }

    @Override
    public ActivityRecord getStates(String id) {
        final ActivityRecord record = activityRecordRepository.findById(id);
        if (record != null) {
            if (record.getStates() == null) {
                // The service registered but has never updated its state or pinged the router
                final ServiceState serviceState = ServiceState.newMissingInAction();
                updateLastState(id, serviceState);
            }
        }

        return record;
    }

    @Override
    public List<ServiceTarget> getEntries(ServiceType serviceType) {
        if (serviceType == ServiceType.TOOL_INVOKER) {
            return toolRepository.listAll();
        } else {
            return resourceTargetRepository.listAll();
        }
    }

    @Override
    public void update(ServiceTarget serviceTarget) {
        register(serviceTarget);
    }

    /**
     * Used for testing
     */
    void clear() {
        resourceTargetRepository.deleteAll();
        toolRepository.deleteAll();
        activityRecordRepository.deleteAll();
    }

    @Override
    public void ping(String id) {
        if (!activityRecordRepository.update(id, InfinispanServiceRegistry::updatePing)) {
            LOG.warn("No records were updated during ping");
        }
    }

    @Override
    public void updateLastState(String id, ServiceState state) {
        activityRecordRepository.update(id, e -> updateLastState(e, state));
    }

    private void updateLastState(ActivityRecord activityRecord, ServiceState state) {
        final List<ServiceState> states = activityRecord.getStates();

        if (states.size() > maxStateCount) {
            for (int i = 0; i < maxStateCount / 2; i++) {
                states.removeFirst();
            }
        }

        states.add(state);
    }

    public int getMaxStateCount() {
        return maxStateCount;
    }

    void setMaxStateCount(int maxStateCount) {
        this.maxStateCount = maxStateCount;
    }
}
