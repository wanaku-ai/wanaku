package ai.wanaku.operator.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.operator.util.OperatorUtil;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuRouter;

/**
 * Registers the Wanaku operator metrics in the Micrometer registry, exposed in Prometheus
 * format at the {@code /metrics} endpoint:
 *
 * <ul>
 *   <li>{@code wanaku_router_reconciliations_total} - total WanakuRouter reconciliations</li>
 *   <li>{@code wanaku_router_reconciliation_errors_total} - failed WanakuRouter reconciliations</li>
 *   <li>{@code wanaku_router_instances} - WanakuRouter instances in the operator namespace</li>
 *   <li>{@code wanaku_router_ready_instances} - WanakuRouter instances whose Ready condition is True</li>
 *   <li>{@code wanaku_toolservice_instances} - WanakuCapability (tool service) instances in the operator namespace</li>
 * </ul>
 */
@ApplicationScoped
public class OperatorMetrics {
    private static final Logger LOG = Logger.getLogger(OperatorMetrics.class);

    private final Counter routerReconciliations;
    private final Counter routerReconciliationErrors;

    @Inject
    public OperatorMetrics(MeterRegistry registry, KubernetesClient kubernetesClient) {
        routerReconciliations = Counter.builder("wanaku.router.reconciliations")
                .description("Total number of WanakuRouter reconciliations")
                .register(registry);

        routerReconciliationErrors = Counter.builder("wanaku.router.reconciliation.errors")
                .description("Total number of failed WanakuRouter reconciliations")
                .register(registry);

        Gauge.builder("wanaku.router.instances", () -> countInstances(kubernetesClient, WanakuRouter.class))
                .description("Number of WanakuRouter instances in the operator namespace")
                .register(registry);

        Gauge.builder("wanaku.router.ready.instances", () -> countReadyRouters(kubernetesClient))
                .description("Number of WanakuRouter instances whose Ready condition is True")
                .register(registry);

        Gauge.builder("wanaku.toolservice.instances", () -> countInstances(kubernetesClient, WanakuCapability.class))
                .description("Number of WanakuCapability (tool service) instances in the operator namespace")
                .register(registry);
    }

    void onStart(@Observes StartupEvent event) {
        // Forces eager instantiation so the gauges are registered before the first scrape
    }

    public void countRouterReconciliation() {
        routerReconciliations.increment();
    }

    public void countRouterReconciliationError() {
        routerReconciliationErrors.increment();
    }

    private static <T extends HasMetadata> double countInstances(KubernetesClient client, Class<T> resourceType) {
        try {
            return client.resources(resourceType)
                    .inNamespace(client.getNamespace())
                    .list()
                    .getItems()
                    .size();
        } catch (Exception e) {
            LOG.debugf(e, "Unable to count %s instances", resourceType.getSimpleName());
            return Double.NaN;
        }
    }

    private static double countReadyRouters(KubernetesClient client) {
        try {
            return client.resources(WanakuRouter.class).inNamespace(client.getNamespace()).list().getItems().stream()
                    .filter(OperatorMetrics::isReady)
                    .count();
        } catch (Exception e) {
            LOG.debugf(e, "Unable to count ready WanakuRouter instances");
            return Double.NaN;
        }
    }

    static boolean isReady(WanakuRouter router) {
        if (router.getStatus() == null) {
            return false;
        }

        final Condition readyCondition =
                OperatorUtil.findCondition(router.getStatus().getConditions(), OperatorUtil.READY_CONDITION);
        return readyCondition != null && OperatorUtil.CONDITION_STATUS_TRUE.equals(readyCondition.getStatus());
    }
}
