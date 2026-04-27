package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import static ai.wanaku.operator.util.OperatorUtil.READY_CONDITION;
import static ai.wanaku.operator.util.OperatorUtil.findCondition;
import static ai.wanaku.operator.util.OperatorUtil.makeCodeExecutionEngineEndpoints;
import static ai.wanaku.operator.util.OperatorUtil.makeCodeExecutionEngineInternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredCamelCodeExecutionEngineDeployment;
import static ai.wanaku.operator.util.OperatorUtil.readyCondition;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE),
        name = "camel-code-execution-engine")
@CSVMetadata(
        displayName = "Camel Code Execution Engine operator",
        description = "Deploys and manages the Camel Code Execution Engine capability")
@RBACRule(
        apiGroups = "",
        resources = {"services", "configmaps", "secrets", "serviceaccounts", "endpoints"},
        verbs = {
            RBACVerbs.GET,
            RBACVerbs.LIST,
            RBACVerbs.WATCH,
            RBACVerbs.CREATE,
            RBACVerbs.UPDATE,
            RBACVerbs.PATCH,
            RBACVerbs.DELETE
        })
@RBACRule(
        apiGroups = "apps",
        resources = {"deployments"},
        verbs = {
            RBACVerbs.GET,
            RBACVerbs.LIST,
            RBACVerbs.WATCH,
            RBACVerbs.CREATE,
            RBACVerbs.UPDATE,
            RBACVerbs.PATCH,
            RBACVerbs.DELETE
        })
public class WanakuCodeExecutionEngineReconciler implements Reconciler<WanakuCodeExecutionEngine> {
    private static final Logger LOG = Logger.getLogger(WanakuCodeExecutionEngineReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<WanakuCodeExecutionEngine> reconcile(
            WanakuCodeExecutionEngine resource, Context<WanakuCodeExecutionEngine> context) throws Exception {
        LOG.infof(
                "Starting code execution engine reconciliation for %s",
                resource.getMetadata().getName());

        validateSpec(resource);

        final String namespace = resource.getMetadata().getNamespace();
        final boolean remote = isRemote(resource);
        final String serviceName = resource.getMetadata().getName();
        final String serviceUrl = buildServiceUrl(resource);

        final Service desiredService = makeCodeExecutionEngineInternalService(resource);

        if (!remote) {
            final Deployment desiredDeployment = makeDesiredCamelCodeExecutionEngineDeployment(resource, context);
            LOG.infof("Creating or updating Deployment %s in %s", serviceName, namespace);
            kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .resource(desiredDeployment)
                    .createOr(Replaceable::update);
        }

        LOG.infof("Creating or updating Service %s in %s", serviceName, namespace);
        kubernetesClient
                .services()
                .inNamespace(namespace)
                .resource(desiredService)
                .createOr(Replaceable::update);

        if (remote) {
            final Endpoints desiredEndpoints = makeCodeExecutionEngineEndpoints(resource);
            kubernetesClient
                    .endpoints()
                    .inNamespace(namespace)
                    .resource(desiredEndpoints)
                    .createOr(Replaceable::update);
        }

        final WanakuCodeExecutionEngineStatus status = new WanakuCodeExecutionEngineStatus();
        status.setDeploymentState(remote ? "REMOTE_READY" : "IN_CLUSTER_READY");
        status.setServiceUrl(serviceUrl);
        status.setActiveRoutes(List.of(buildRouteIdentifier(resource)));
        status.setHealthChecks(List.of(buildHealthCheck(resource, remote, serviceName, namespace)));

        final Condition previousReadyCondition = findCondition(
                resource.getStatus() != null ? resource.getStatus().getConditions() : null, READY_CONDITION);
        status.setConditions(List.of(readyCondition(
                resource.getMetadata().getGeneration(),
                previousReadyCondition,
                "Camel Code Execution Engine is ready")));
        resource.setStatus(status);

        return UpdateControl.patchStatus(resource);
    }

    private static String buildRouteIdentifier(WanakuCodeExecutionEngine resource) {
        return String.format(
                Locale.ROOT,
                "%s/%s",
                resource.getSpec().getEngineType(),
                resource.getSpec().getLanguageName());
    }

    private WanakuCodeExecutionEngineStatus.HealthCheck buildHealthCheck(
            WanakuCodeExecutionEngine resource, boolean remote, String serviceName, String namespace) {
        WanakuCodeExecutionEngineStatus.HealthCheck healthCheck = new WanakuCodeExecutionEngineStatus.HealthCheck();
        healthCheck.setName(remote ? "remote-endpoint" : "deployment");
        healthCheck.setTimestamp(OffsetDateTime.now().toString());

        if (remote) {
            healthCheck.setStatus("True");
            healthCheck.setMessage("Remote code execution engine target configured at " + buildServiceUrl(resource));
            return healthCheck;
        }

        Deployment deployment = kubernetesClient
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(serviceName)
                .get();
        boolean healthy = deployment != null;
        healthCheck.setStatus(healthy ? "True" : "False");
        healthCheck.setMessage(
                healthy ? "In-cluster deployment is present" : "In-cluster deployment has not been observed yet");
        return healthCheck;
    }

    private String buildServiceUrl(WanakuCodeExecutionEngine resource) {
        String scheme = resource.getSpec().getRemote() != null
                        && resource.getSpec().getRemote().getScheme() != null
                ? resource.getSpec().getRemote().getScheme()
                : "http";
        Integer port = resource.getSpec().getPort() != null ? resource.getSpec().getPort() : 9190;

        if (isRemote(resource)) {
            String path = resource.getSpec().getRemote().getPath();
            String suffix = path != null && !path.isBlank() ? path : "";
            return String.format(
                    Locale.ROOT,
                    "%s://%s:%d%s",
                    scheme,
                    resource.getSpec().getRemote().getHost(),
                    resource.getSpec().getRemote().getPort() != null
                            ? resource.getSpec().getRemote().getPort()
                            : port,
                    suffix);
        }

        // For in-cluster, always use http since it's internal cluster communication
        return String.format(
                Locale.ROOT,
                "http://%s.%s.svc.cluster.local:%d",
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace(),
                port);
    }

    private void validateSpec(WanakuCodeExecutionEngine resource) {
        final WanakuCodeExecutionEngineSpec spec = resource.getSpec();
        if (spec == null) {
            throw new WanakuException("spec must be provided");
        }
        if (spec.getRouterRef() == null || spec.getRouterRef().isBlank()) {
            throw new WanakuException("routerRef must be specified for the Camel Code Execution Engine");
        }
        if (spec.getLanguageName() == null || spec.getLanguageName().isBlank()) {
            throw new WanakuException("languageName must be specified for the Camel Code Execution Engine");
        }
        if (spec.getEngineType() == null || spec.getEngineType().isBlank()) {
            throw new WanakuException("engineType must be specified for the Camel Code Execution Engine");
        }

        if (isRemote(resource)) {
            if (spec.getRemote() == null
                    || spec.getRemote().getHost() == null
                    || spec.getRemote().getHost().isBlank()) {
                throw new WanakuException("remote.host must be specified when deploymentMode=remote");
            }
        } else if (spec.getImage() == null || spec.getImage().isBlank()) {
            throw new WanakuException("image must be specified when deploymentMode=in-cluster");
        }

        validateSecurityLists(spec.getSecurity());
        validateCacheStrategy(spec.getDependencyCache());
    }

    private void validateSecurityLists(WanakuCodeExecutionEngineSpec.SecuritySpec securitySpec) {
        if (securitySpec == null) {
            return;
        }

        validateNoOverlap("component", securitySpec.getComponentAllowlist(), securitySpec.getComponentBlocklist());
        validateNoOverlap("endpoint", securitySpec.getEndpointAllowlist(), securitySpec.getEndpointBlocklist());
        validateNoOverlap("route", securitySpec.getRouteAllowlist(), securitySpec.getRouteBlocklist());
    }

    private void validateCacheStrategy(WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec) {
        if (cacheSpec == null
                || cacheSpec.getStrategy() == null
                || cacheSpec.getStrategy().isBlank()) {
            return;
        }

        String strategy = cacheSpec.getStrategy().trim().toLowerCase(Locale.ROOT);
        if (!WanakuTypes.VALID_CACHE_STRATEGIES.contains(strategy)) {
            throw new WanakuException("dependencyCache.strategy must be one of: "
                    + String.join(", ", WanakuTypes.VALID_CACHE_STRATEGIES));
        }
    }

    private void validateNoOverlap(String name, List<String> allowlist, List<String> blocklist) {
        if (allowlist == null || allowlist.isEmpty() || blocklist == null || blocklist.isEmpty()) {
            return;
        }

        List<String> overlaps = new ArrayList<>();
        for (String entry : allowlist) {
            if (blocklist.contains(entry)) {
                overlaps.add(entry);
            }
        }

        if (!overlaps.isEmpty()) {
            throw new WanakuException(
                    String.format("%s allowlist and blocklist cannot contain the same entries: %s", name, overlaps));
        }
    }

    private boolean isRemote(WanakuCodeExecutionEngine resource) {
        String deploymentMode = resource.getSpec().getDeploymentMode();
        return deploymentMode != null && WanakuTypes.DEPLOYMENT_MODE_REMOTE.equalsIgnoreCase(deploymentMode.trim());
    }
}
