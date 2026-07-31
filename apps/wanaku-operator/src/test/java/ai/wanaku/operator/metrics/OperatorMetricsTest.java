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
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCamelRoute;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterStatus;
import ai.wanaku.operator.wanaku.WanakuServiceCatalog;

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
        metrics = new OperatorMetrics(registry, kubernetesClient, true);
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
    void taggedCountersIncrementPerController() {
        metrics.countReconciliation(OperatorMetrics.CONTROLLER_CAPABILITY);
        metrics.countReconciliation(OperatorMetrics.CONTROLLER_CAPABILITY);
        metrics.countReconciliation(OperatorMetrics.CONTROLLER_CAMEL_ROUTE);
        metrics.countReconciliationError(OperatorMetrics.CONTROLLER_CAPABILITY);

        assertEquals(
                2.0,
                registry.get("wanaku.reconciliations")
                        .tag("controller", OperatorMetrics.CONTROLLER_CAPABILITY)
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.get("wanaku.reconciliations")
                        .tag("controller", OperatorMetrics.CONTROLLER_CAMEL_ROUTE)
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.get("wanaku.reconciliation.errors")
                        .tag("controller", OperatorMetrics.CONTROLLER_CAPABILITY)
                        .counter()
                        .count());
    }

    @Test
    void routerCountersAlsoIncrementTaggedCounters() {
        metrics.countRouterReconciliation();
        metrics.countRouterReconciliationError();

        assertEquals(
                1.0,
                registry.get("wanaku.reconciliations")
                        .tag("controller", OperatorMetrics.CONTROLLER_ROUTER)
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.get("wanaku.reconciliation.errors")
                        .tag("controller", OperatorMetrics.CONTROLLER_ROUTER)
                        .counter()
                        .count());
    }

    @Test
    void taggedCountersAreRegisteredEagerlyForAllControllers() {
        for (String controller : List.of(
                OperatorMetrics.CONTROLLER_ROUTER,
                OperatorMetrics.CONTROLLER_CAPABILITY,
                OperatorMetrics.CONTROLLER_SERVICE_CATALOG,
                OperatorMetrics.CONTROLLER_CAMEL_ROUTE,
                OperatorMetrics.CONTROLLER_CODE_EXECUTION_ENGINE)) {
            assertEquals(
                    0.0,
                    registry.get("wanaku.reconciliations")
                            .tag("controller", controller)
                            .counter()
                            .count());
            assertEquals(
                    0.0,
                    registry.get("wanaku.reconciliation.errors")
                            .tag("controller", controller)
                            .counter()
                            .count());
        }
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
    void remainingCrGaugesCountInstances() {
        mockResourceList(WanakuServiceCatalog.class, List.of(new WanakuServiceCatalog()));
        mockResourceList(WanakuCamelRoute.class, List.of(new WanakuCamelRoute(), new WanakuCamelRoute()));
        mockResourceList(WanakuCamelCodeExecutionEngine.class, List.of());

        assertEquals(
                1.0, registry.get("wanaku.servicecatalog.instances").gauge().value());
        assertEquals(2.0, registry.get("wanaku.camelroute.instances").gauge().value());
        assertEquals(
                0.0,
                registry.get("wanaku.codeexecutionengine.instances").gauge().value());
    }

    @Test
    void gaugesReturnNaNWhenTheApiIsUnavailable() {
        when(kubernetesClient.resources(WanakuRouter.class)).thenThrow(new RuntimeException("API unavailable"));

        assertTrue(Double.isNaN(registry.get("wanaku.router.instances").gauge().value()));
        assertTrue(Double.isNaN(
                registry.get("wanaku.router.ready.instances").gauge().value()));
    }

    @Test
    void disabledMetricsRegisterNothingAndCountersAreNoOps() {
        SimpleMeterRegistry emptyRegistry = new SimpleMeterRegistry();
        OperatorMetrics disabled = new OperatorMetrics(emptyRegistry, kubernetesClient, false);

        disabled.countRouterReconciliation();
        disabled.countRouterReconciliationError();
        disabled.countReconciliation(OperatorMetrics.CONTROLLER_CAPABILITY);
        disabled.countReconciliationError(OperatorMetrics.CONTROLLER_CAPABILITY);

        assertTrue(emptyRegistry.getMeters().isEmpty());
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
