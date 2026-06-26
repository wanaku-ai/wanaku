package ai.wanaku.backend.health;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.WanakuRouterConfig;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.common.ServiceTargetEvent;
import ai.wanaku.backend.core.mcp.providers.ServiceRegistry;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Periodically probes registered capabilities for health status via gRPC.
 */
@ApplicationScoped
public class PeriodicHealthCheckService {
    private static final Logger LOG = Logger.getLogger(PeriodicHealthCheckService.class);

    public static final String HEALTH_CHECK_ADDRESS = "health-check";

    private final ConcurrentHashMap<String, Boolean> inProgress = new ConcurrentHashMap<>();

    @Inject
    WanakuRouterConfig config;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    WanakuBridgeTransport transport;

    @Inject
    @Channel("service-target-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ServiceTargetEvent> serviceTargetEventEmitter;

    private ServiceRegistry serviceRegistry;
    private HealthProbeClient probeClient;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
        probeClient = new HealthProbeClient(transport);
    }

    @Scheduled(
            every = "${wanaku.router.health-check.interval-seconds:60}s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        if (!config.healthCheck().enabled()) {
            return;
        }

        List<ServiceTarget> entries = serviceRegistry.getEntries();
        LOG.infof("Health check sweep: checking %d registered capabilities", entries.size());

        int maxConcurrent = Math.max(0, config.healthCheck().maxConcurrent());
        int budget = maxConcurrent - inProgress.size();
        if (budget <= 0) {
            LOG.debugf(
                    "Health check sweep: %d checks already in progress (max %d), skipping",
                    inProgress.size(), maxConcurrent);
            return;
        }

        int scheduled = 0;
        for (ServiceTarget target : entries) {
            if (scheduled >= budget) {
                break;
            }
            managedExecutor.submit(() -> checkInstanceHealth(target));
            scheduled++;
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

        // Skip capabilities that were explicitly deregistered.
        // Capabilities that crashed without deregistering remain ACTIVE
        // and will still be probed and marked as DOWN.
        ActivityRecord activityRecord = serviceRegistry.getStates(id);
        if (activityRecord == null) {
            LOG.infof(
                    "Skipping health check for capability without activity record %s (%s)",
                    target.getServiceName(), id);
            return;
        }

        if (!activityRecord.isActive()) {
            LOG.infof("Skipping health check for deregistered capability %s (%s)", target.getServiceName(), id);
            return;
        }

        int port = target.getPort();
        if (port < 1 || port > 65535) {
            LOG.warnf("Invalid port %d for capability %s (%s), marking as DOWN", port, target.getServiceName(), id);
            serviceRegistry.updateHealthStatus(id, HealthStatus.DOWN);
            return;
        }

        if (inProgress.putIfAbsent(id, Boolean.TRUE) != null) {
            LOG.infof("Health check already in progress for %s, skipping", id);
            return;
        }

        probeClient
                .probeAsync(target)
                .subscribe()
                .with(
                        status -> completeHealthCheck(target, id, status),
                        failure -> failHealthCheck(id, activityRecord, failure));
    }

    private void completeHealthCheck(ServiceTarget target, String id, HealthStatus status) {
        try {
            LOG.infof("Health probe result for %s (%s): %s", target.getServiceName(), id, status.asValue());

            if (status == HealthStatus.DOWN) {
                ActivityRecord activityRecord = serviceRegistry.getStates(id);
                if (activityRecord != null && activityRecord.getHealthStatus() == HealthStatus.PENDING) {
                    final Duration since = Duration.between(activityRecord.getLastSeen(), Instant.now());
                    if (since.toMinutes() < ActivityRecord.TIME_TO_LET_GO) {
                        LOG.infof(
                                "Keeping capability %s (%s) in PENDING state (registered %d seconds ago, grace period %d minutes)",
                                target.getServiceName(), id, since.getSeconds(), ActivityRecord.TIME_TO_LET_GO);
                        return;
                    }
                }
            }

            serviceRegistry.updateHealthStatus(id, status);
            emitHealthStatusEvent(id, status);
        } finally {
            inProgress.remove(id);
        }
    }

    private void failHealthCheck(String id, ActivityRecord activityRecord, Throwable failure) {
        try {
            LOG.infof(
                    "Health probe failed for capability %s, current status: %s",
                    id, activityRecord.getHealthStatus().asValue());
            if (activityRecord.getHealthStatus() == HealthStatus.PENDING) {
                final Instant lastSeen = activityRecord.getLastSeen();
                final Duration between = Duration.between(lastSeen, Instant.now());

                if (between.toMinutes() < ActivityRecord.TIME_TO_LET_GO) {
                    LOG.infof("Recently registered capability %s is in pending health state. ", id);
                } else {
                    markDown(id, failure);
                }
            } else {
                markDown(id, failure);
            }
        } finally {
            inProgress.remove(id);
        }
    }

    private void markDown(String id, Throwable failure) {
        LOG.warnf(failure, "Error during health check for %s", id);
        serviceRegistry.updateHealthStatus(id, HealthStatus.DOWN);
        emitHealthStatusEvent(id, HealthStatus.DOWN);
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
     * Dispatches the health probe asynchronously.
     *
     * @param target the service target to check
     */
    @ConsumeEvent(HEALTH_CHECK_ADDRESS)
    void onHealthCheckRequested(ServiceTarget target) {
        checkInstanceHealth(target);
    }
}
