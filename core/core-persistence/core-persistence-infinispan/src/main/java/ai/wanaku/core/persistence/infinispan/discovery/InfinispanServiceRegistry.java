package ai.wanaku.core.persistence.infinispan.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.StaleCapability;

public class InfinispanServiceRegistry implements ServiceRegistry {
    private static final Logger LOG = Logger.getLogger(InfinispanServiceRegistry.class);

    private final InfinispanCapabilitiesRepository capabilitiesRepository;
    private final InfinispanServiceRecordRepository activityRecordRepository;

    private int maxStateCount;

    public InfinispanServiceRegistry(
            InfinispanCapabilitiesRepository capabilitiesRepository,
            InfinispanServiceRecordRepository activityRecordRepository) {
        this.capabilitiesRepository = capabilitiesRepository;
        this.activityRecordRepository = activityRecordRepository;
    }

    private static void updatePing(ActivityRecord e) {
        e.setLastSeen(Instant.now());
        e.setActive(true);
    }

    @Override
    public ServiceTarget register(ServiceTarget serviceTarget) {
        return capabilitiesRepository.persist(serviceTarget);
    }

    @Override
    public void deregister(ServiceTarget serviceTarget) {
        capabilitiesRepository.deleteById(serviceTarget.getId());

        activityRecordRepository.update(serviceTarget.getId(), a -> applyDeregistration(serviceTarget.getId(), a));
    }

    private void applyDeregistration(String id, ActivityRecord activityRecord) {
        activityRecord.setLastSeen(Instant.now());
        activityRecord.setActive(false);
        updateLastState(id, ServiceState.newInactive());
    }

    @Override
    public List<ServiceTarget> getServiceByName(String serviceName, String serviceType) {
        return capabilitiesRepository.findByService(serviceName, serviceType);
    }

    @Override
    public List<ServiceTarget> getCodeExecutionService(String serviceType, String serviceSubType, String serviceName) {
        return capabilitiesRepository.findCodeExecutionService(serviceType, serviceSubType, serviceName);
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
    public List<ServiceTarget> getEntries() {
        return capabilitiesRepository.listAll();
    }

    @Override
    public List<ServiceTarget> getEntries(String serviceType) {
        return capabilitiesRepository.listCapable(serviceType);
    }

    @Override
    public void update(ServiceTarget serviceTarget) {
        register(serviceTarget);
    }

    /**
     * Used for testing
     */
    void clear() {
        capabilitiesRepository.deleteAll();
        activityRecordRepository.deleteAll();
    }

    @Override
    public void ping(String id) {
        LOG.tracef("Service %s has pinged", id);
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

    @Override
    public List<StaleCapability> findStaleCapabilities(long maxAgeSeconds, boolean inactiveOnly) {
        List<StaleCapability> staleCapabilities = new ArrayList<>();
        Instant threshold = Instant.now().minusSeconds(maxAgeSeconds);

        List<ServiceTarget> allCapabilities = capabilitiesRepository.listAll();
        for (ServiceTarget serviceTarget : allCapabilities) {
            ActivityRecord activityRecord = activityRecordRepository.findById(serviceTarget.getId());

            if (activityRecord == null) {
                // No activity record means it was never pinged - consider it stale
                staleCapabilities.add(new StaleCapability(serviceTarget, null));
                continue;
            }

            Instant lastSeen = activityRecord.getLastSeen();
            boolean isOldEnough = lastSeen == null || lastSeen.isBefore(threshold);
            boolean isInactive = !activityRecord.isActive();

            if (inactiveOnly) {
                // Only include if both old enough AND inactive
                if (isOldEnough && isInactive) {
                    staleCapabilities.add(new StaleCapability(serviceTarget, activityRecord));
                }
            } else {
                // Include if old enough, regardless of active status
                if (isOldEnough) {
                    staleCapabilities.add(new StaleCapability(serviceTarget, activityRecord));
                }
            }
        }

        return staleCapabilities;
    }

    @Override
    public boolean removeById(String id) {
        boolean capabilityRemoved = capabilitiesRepository.deleteById(id);
        boolean activityRemoved = activityRecordRepository.deleteById(id);

        LOG.debugf("Removed capability %s: capability=%b, activity=%b", id, capabilityRemoved, activityRemoved);

        return capabilityRemoved || activityRemoved;
    }
}
