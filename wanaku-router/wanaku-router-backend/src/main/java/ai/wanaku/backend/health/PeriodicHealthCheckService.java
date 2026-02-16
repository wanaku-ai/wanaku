package ai.wanaku.backend.health;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.common.ServiceTargetEvent;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceRegistry;

/**
 * Periodically probes registered capabilities for health status via gRPC.
 */
@ApplicationScoped
public class PeriodicHealthCheckService {
    private static final Logger LOG = Logger.getLogger(PeriodicHealthCheckService.class);

    public static final String HEALTH_CHECK_ADDRESS = "health-check";

    private final ConcurrentHashMap<String, Boolean> inProgress = new ConcurrentHashMap<>();

    @Inject
    HealthCheckConfig config;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    @Channel("service-target-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ServiceTargetEvent> serviceTargetEventEmitter;

    private ServiceRegistry serviceRegistry;
    private HealthProbeClient probeClient;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
        probeClient = new HealthProbeClient(config.timeoutSeconds());
    }

    @Scheduled(
            every = "${wanaku.router.health-check.interval-seconds:60}s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        if (!config.enabled()) {
            return;
        }

        List<ServiceTarget> entries = serviceRegistry.getEntries();
        LOG.debugf("Health check sweep: checking %d capabilities", entries.size());

        for (ServiceTarget target : entries) {
            managedExecutor.submit(() -> checkInstanceHealth(target));
        }
    }

    /**
     * Checks the health of a single capability instance.
     * This method is safe to call from multiple threads and handles deduplication.
     *
     * @param target the service target to check
     */
    public void checkInstanceHealth(ServiceTarget target) {
        String id = target.getId();
        if (id == null) {
            return;
        }

        if (inProgress.putIfAbsent(id, Boolean.TRUE) != null) {
            LOG.tracef("Health check already in progress for %s, skipping", id);
            return;
        }

        try {
            HealthStatus status = probeClient.probe(target);
            LOG.debugf("Health probe result for %s (%s): %s", target.getServiceName(), id, status.asValue());
            serviceRegistry.updateHealthStatus(id, status);
            emitHealthStatusEvent(id, status);
        } catch (Exception e) {
            LOG.warnf(e, "Error during health check for %s", id);
            serviceRegistry.updateHealthStatus(id, HealthStatus.DOWN);
            emitHealthStatusEvent(id, HealthStatus.DOWN);
        } finally {
            inProgress.remove(id);
        }
    }

    private void emitHealthStatusEvent(String id, HealthStatus status) {
        if (!serviceTargetEventEmitter.hasRequests()) {
            return;
        }

        ServiceState state =
                switch (status) {
                    case HEALTHY -> ServiceState.newHealthy();
                    case UNHEALTHY -> ServiceState.newUnhealthy("health probe reported unhealthy");
                    case DOWN -> ServiceState.newDown("health probe reported down");
                    default -> ServiceState.newPending();
                };

        serviceTargetEventEmitter.sendAndForget(ServiceTargetEvent.update(id, state));
    }

    /**
     * Consumes health check events from the Vert.x EventBus.
     * Runs on a worker thread since the health probe involves blocking gRPC calls.
     *
     * @param target the service target to check
     */
    @ConsumeEvent(value = HEALTH_CHECK_ADDRESS, blocking = true)
    void onHealthCheckRequested(ServiceTarget target) {
        checkInstanceHealth(target);
    }
}
