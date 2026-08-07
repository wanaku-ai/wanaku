package ai.wanaku.operator.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.operator.util.OperatorUtil;
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCamelRoute;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuServiceCatalog;

/**
 * Registers the Wanaku operator metrics in the Micrometer registry, exposed in Prometheus
 * format at the {@code /metrics} endpoint:
 *
 * <ul>
 *   <li>{@code wanaku_reconciliations_total} - reconciliations, labeled by {@code controller}</li>
 *   <li>{@code wanaku_reconciliation_errors_total} - failed reconciliations (thrown or resolved
 *       to an error status), labeled by {@code controller}</li>
 *   <li>{@code wanaku_router_reconciliations_total} - total WanakuRouter reconciliations
 *       (kept for compatibility; equivalent to {@code controller="wanaku-router"})</li>
 *   <li>{@code wanaku_router_reconciliation_errors_total} - failed WanakuRouter reconciliations
 *       (kept for compatibility)</li>
 *   <li>{@code wanaku_router_instances} - WanakuRouter instances in the operator namespace</li>
 *   <li>{@code wanaku_router_ready_instances} - WanakuRouter instances whose Ready condition is True</li>
 *   <li>{@code wanaku_toolservice_instances} - WanakuCapability (tool service) instances in the operator namespace</li>
 *   <li>{@code wanaku_servicecatalog_instances} - WanakuServiceCatalog instances in the operator namespace</li>
 *   <li>{@code wanaku_camelroute_instances} - WanakuCamelRoute instances in the operator namespace</li>
 *   <li>{@code wanaku_codeexecutionengine_instances} - WanakuCamelCodeExecutionEngine instances in the operator
 *       namespace</li>
 * </ul>
 *
 * <p>Collection can be turned off with {@code wanaku.operator.metrics.enabled=false} (default {@code true}),
 * in which case no Wanaku meter is registered and the gauges never query the Kubernetes API.</p>
 */
@ApplicationScoped
public class OperatorMetrics {
    private static final Logger LOG = Logger.getLogger(OperatorMetrics.class);

    public static final String CONTROLLER_ROUTER = "wanaku-router";
    public static final String CONTROLLER_CAPABILITY = "wanaku-capability";
    public static final String CONTROLLER_SERVICE_CATALOG = "wanaku-service-catalog";
    public static final String CONTROLLER_CAMEL_ROUTE = "wanaku-camel-route";
    public static final String CONTROLLER_CODE_EXECUTION_ENGINE = "camel-code-execution-engine";

    private static final List<String> CONTROLLERS = List.of(
            CONTROLLER_ROUTER,
            CONTROLLER_CAPABILITY,
            CONTROLLER_SERVICE_CATALOG,
            CONTROLLER_CAMEL_ROUTE,
            CONTROLLER_CODE_EXECUTION_ENGINE);

    private final MeterRegistry registry;
    private final boolean enabled;
    private final Counter routerReconciliations;
    private final Counter routerReconciliationErrors;

    @Inject
    public OperatorMetrics(
            MeterRegistry registry,
            KubernetesClient kubernetesClient,
            @ConfigProperty(name = "wanaku.operator.metrics.enabled", defaultValue = "true") boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;

        if (!enabled) {
            LOG.info("Wanaku operator metrics are disabled (wanaku.operator.metrics.enabled=false)");
            routerReconciliations = null;
            routerReconciliationErrors = null;
            return;
        }

        routerReconciliations = Counter.builder("wanaku.router.reconciliations")
                .description("Total number of WanakuRouter reconciliations")
                .register(registry);

        routerReconciliationErrors = Counter.builder("wanaku.router.reconciliation.errors")
                .description("Total number of failed WanakuRouter reconciliations")
                .register(registry);

        // Eagerly register the per-controller counters so all series appear from the first scrape
        for (String controller : CONTROLLERS) {
            reconciliations(controller);
            reconciliationErrors(controller);
        }

        Gauge.builder("wanaku.router.instances", () -> countInstances(kubernetesClient, WanakuRouter.class))
                .description("Number of WanakuRouter instances in the operator namespace")
                .register(registry);

        Gauge.builder("wanaku.router.ready.instances", () -> countReadyRouters(kubernetesClient))
                .description("Number of WanakuRouter instances whose Ready condition is True")
                .register(registry);

        Gauge.builder("wanaku.toolservice.instances", () -> countInstances(kubernetesClient, WanakuCapability.class))
                .description("Number of WanakuCapability (tool service) instances in the operator namespace")
                .register(registry);

        Gauge.builder(
                        "wanaku.servicecatalog.instances",
                        () -> countInstances(kubernetesClient, WanakuServiceCatalog.class))
                .description("Number of WanakuServiceCatalog instances in the operator namespace")
                .register(registry);

        Gauge.builder("wanaku.camelroute.instances", () -> countInstances(kubernetesClient, WanakuCamelRoute.class))
                .description("Number of WanakuCamelRoute instances in the operator namespace")
                .register(registry);

        Gauge.builder(
                        "wanaku.codeexecutionengine.instances",
                        () -> countInstances(kubernetesClient, WanakuCamelCodeExecutionEngine.class))
                .description("Number of WanakuCamelCodeExecutionEngine instances in the operator namespace")
                .register(registry);
    }

    void onStart(@Observes StartupEvent event) {
        // Forces eager instantiation so the gauges are registered before the first scrape
    }

    public void countReconciliation(String controller) {
        if (!enabled) {
            return;
        }
        reconciliations(controller).increment();
    }

    public void countReconciliationError(String controller) {
        if (!enabled) {
            return;
        }
        reconciliationErrors(controller).increment();
    }

    public void countRouterReconciliation() {
        if (!enabled) {
            return;
        }
        routerReconciliations.increment();
        countReconciliation(CONTROLLER_ROUTER);
    }

    public void countRouterReconciliationError() {
        if (!enabled) {
            return;
        }
        routerReconciliationErrors.increment();
        countReconciliationError(CONTROLLER_ROUTER);
    }

    private Counter reconciliations(String controller) {
        return Counter.builder("wanaku.reconciliations")
                .description("Total number of reconciliations per controller")
                .tag("controller", controller)
                .register(registry);
    }

    private Counter reconciliationErrors(String controller) {
        return Counter.builder("wanaku.reconciliation.errors")
                .description("Total number of failed reconciliations per controller")
                .tag("controller", controller)
                .register(registry);
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
