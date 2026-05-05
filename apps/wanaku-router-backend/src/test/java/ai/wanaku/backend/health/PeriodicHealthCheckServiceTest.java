package ai.wanaku.backend.health;

import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.context.ManagedExecutor;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.common.ServiceTargetEvent;
import ai.wanaku.backend.core.mcp.providers.ServiceRegistry;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PeriodicHealthCheckService}.
 */
class PeriodicHealthCheckServiceTest {

    private static final String SERVICE_ID = "test-service-id";
    private static final String SERVICE_NAME = "test-service";

    private PeriodicHealthCheckService healthCheckService;
    private ServiceRegistry serviceRegistry;
    private HealthProbeClient probeClient;
    private ManagedExecutor managedExecutor;
    private MutinyEmitter<ServiceTargetEvent> eventEmitter;

    @BeforeEach
    void setUp() {
        healthCheckService = new PeriodicHealthCheckService();
        serviceRegistry = mock(ServiceRegistry.class);
        probeClient = mock(HealthProbeClient.class);
        managedExecutor = mock(ManagedExecutor.class);
        eventEmitter = mock(MutinyEmitter.class);

        // Inject mocks using reflection
        injectField(healthCheckService, "serviceRegistry", serviceRegistry);
        injectField(healthCheckService, "probeClient", probeClient);
        injectField(healthCheckService, "managedExecutor", managedExecutor);
        injectField(healthCheckService, "serviceTargetEventEmitter", eventEmitter);

        when(managedExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        when(eventEmitter.hasRequests()).thenReturn(false);
    }

    @Test
    void checkInstanceHealth_skipsDeregisteredCapability() {
        // Given: a service target that was already captured in the sweep
        ServiceTarget target =
                new ServiceTarget(SERVICE_ID, SERVICE_NAME, "localhost", 8080, "tool-invoker", "mcp", null, null, null);

        // And: the capability was deregistered (marked as inactive)
        ActivityRecord inactiveRecord = createInactiveRecord();
        when(serviceRegistry.getStates(SERVICE_ID)).thenReturn(inactiveRecord);

        // When: checkInstanceHealth is called
        healthCheckService.checkInstanceHealth(target);

        // Then: the probe should NOT be called
        verify(probeClient, never()).probe(any());
        verify(serviceRegistry, never()).updateHealthStatus(any(), any());
    }

    @Test
    void checkInstanceHealth_probesActiveCapability() {
        // Given: a service target that is still active
        ServiceTarget target =
                new ServiceTarget(SERVICE_ID, SERVICE_NAME, "localhost", 8080, "tool-invoker", "mcp", null, null, null);

        // And: the capability is active
        ActivityRecord activeRecord = createActiveRecord();
        when(serviceRegistry.getStates(SERVICE_ID)).thenReturn(activeRecord);
        when(probeClient.probe(target)).thenReturn(HealthStatus.HEALTHY);

        // When: checkInstanceHealth is called
        healthCheckService.checkInstanceHealth(target);

        // Then: the probe should be called
        verify(probeClient).probe(target);
        verify(serviceRegistry).updateHealthStatus(SERVICE_ID, HealthStatus.HEALTHY);
    }

    @Test
    void checkInstanceHealth_skipsWhenNoActivityRecord() {
        // Given: a service target with no activity record
        ServiceTarget target =
                new ServiceTarget(SERVICE_ID, SERVICE_NAME, "localhost", 8080, "tool-invoker", "mcp", null, null, null);

        // And: no activity record exists
        when(serviceRegistry.getStates(SERVICE_ID)).thenReturn(null);

        // When: checkInstanceHealth is called
        healthCheckService.checkInstanceHealth(target);

        // Then: the probe should NOT be called (no record = skip)
        verify(probeClient, never()).probe(any());
        verify(serviceRegistry, never()).updateHealthStatus(any(), any());
    }

    @Test
    void checkInstanceHealth_probesCrashedCapability() {
        // Given: a service target that crashed without deregistering
        ServiceTarget target =
                new ServiceTarget(SERVICE_ID, SERVICE_NAME, "localhost", 8080, "tool-invoker", "mcp", null, null, null);

        // And: the capability was previously healthy but crashed without deregistering
        ActivityRecord crashedRecord = createActiveRecord();
        crashedRecord.setHealthStatus(HealthStatus.HEALTHY);
        crashedRecord.setLastSeen(java.time.Instant.now());
        when(serviceRegistry.getStates(SERVICE_ID)).thenReturn(crashedRecord);
        when(probeClient.probe(target)).thenThrow(new RuntimeException("Connection refused"));

        // When: checkInstanceHealth is called
        healthCheckService.checkInstanceHealth(target);

        // Then: the probe should be called and mark as DOWN
        verify(probeClient).probe(target);
        verify(serviceRegistry).updateHealthStatus(SERVICE_ID, HealthStatus.DOWN);
    }

    private ActivityRecord createActiveRecord() {
        ActivityRecord record = new ActivityRecord();
        record.setStates(new java.util.ArrayList<>());
        return record;
    }

    private ActivityRecord createInactiveRecord() {
        ActivityRecord record = new ActivityRecord();
        record.setHealthStatus(HealthStatus.DOWN);
        record.setStates(new java.util.ArrayList<>());
        record.getStates().add(ServiceState.newInactive());
        return record;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field " + fieldName, e);
        }
    }
}
