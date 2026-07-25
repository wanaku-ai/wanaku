package ai.wanaku.operator.metrics;

import java.util.List;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ai.wanaku.operator.util.OperatorUtil;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorMetricsTest {
    private static final String NAMESPACE = "test-ns";

    private KubernetesClient kubernetesClient;
    private SimpleMeterRegistry registry;
    private OperatorMetrics metrics;

    @BeforeEach
    void setUp() {
        kubernetesClient = mock(KubernetesClient.class);
        when(kubernetesClient.getNamespace()).thenReturn(NAMESPACE);
        registry = new SimpleMeterRegistry();
        metrics = new OperatorMetrics(registry, kubernetesClient);
    }

    @SuppressWarnings("unchecked")
    private <T extends HasMetadata> void mockResourceList(Class<T> resourceType, List<T> items) {
        MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operation = mock(MixedOperation.class);
        NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> namespacedOperation =
                mock(NonNamespaceOperation.class);
        KubernetesResourceList<T> resourceList = mock(KubernetesResourceList.class);

        when(kubernetesClient.resources(resourceType)).thenReturn(operation);
        when(operation.inNamespace(NAMESPACE)).thenReturn(namespacedOperation);
        when(namespacedOperation.list()).thenReturn(resourceList);
        when(resourceList.getItems()).thenReturn(items);
    }

    @Test
    void reconciliationCountersIncrement() {
        metrics.countRouterReconciliation();
        metrics.countRouterReconciliation();
        metrics.countRouterReconciliationError();

        assertEquals(
                2.0, registry.get("wanaku.router.reconciliations").counter().count());
        assertEquals(
                1.0,
                registry.get("wanaku.router.reconciliation.errors").counter().count());
    }

    @Test
    void routerGaugesCountInstancesAndReadyInstances() {
        mockResourceList(WanakuRouter.class, List.of(router(true), router(false), new WanakuRouter()));

        assertEquals(3.0, registry.get("wanaku.router.instances").gauge().value());
        assertEquals(1.0, registry.get("wanaku.router.ready.instances").gauge().value());
    }

    @Test
    void toolServiceGaugeCountsCapabilities() {
        mockResourceList(WanakuCapability.class, List.of(new WanakuCapability(), new WanakuCapability()));

        assertEquals(2.0, registry.get("wanaku.toolservice.instances").gauge().value());
    }

    @Test
    void gaugesReturnNaNWhenTheApiIsUnavailable() {
        when(kubernetesClient.resources(WanakuRouter.class)).thenThrow(new RuntimeException("API unavailable"));

        assertTrue(Double.isNaN(registry.get("wanaku.router.instances").gauge().value()));
        assertTrue(Double.isNaN(
                registry.get("wanaku.router.ready.instances").gauge().value()));
    }

    @Test
    void isReadyChecksTheReadyCondition() {
        assertTrue(OperatorMetrics.isReady(router(true)));
        assertFalse(OperatorMetrics.isReady(router(false)));
        assertFalse(OperatorMetrics.isReady(new WanakuRouter()));
    }

    private static WanakuRouter router(boolean ready) {
        WanakuRouter router = new WanakuRouter();
        WanakuRouterStatus status = new WanakuRouterStatus();
        status.setConditions(List.of(new ConditionBuilder()
                .withType(OperatorUtil.READY_CONDITION)
                .withStatus(ready ? OperatorUtil.CONDITION_STATUS_TRUE : OperatorUtil.CONDITION_STATUS_FALSE)
                .build()));
        router.setStatus(status);
        return router;
    }
}
