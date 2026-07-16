package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.OperatorUtil;

import static ai.wanaku.operator.util.CodeExecutionEngineResourceFactory.makeCodeExecutionEngineInternalService;
import static ai.wanaku.operator.util.CodeExecutionEngineResourceFactory.makeDesiredCamelCodeExecutionEngineDeployment;
import static ai.wanaku.operator.util.Matchers.match;

@ControllerConfiguration(
        informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE),
        name = "camel-code-execution-engine")
@CSVMetadata(
        displayName = "Camel Code Execution Engine operator",
        description = "Deploys and manages the Camel Code Execution Engine capability")
@RBACRule(
        apiGroups = "",
        resources = {"services", "configmaps", "secrets", "serviceaccounts"},
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
public class WanakuCamelCodeExecutionEngineReconciler implements Reconciler<WanakuCamelCodeExecutionEngine> {

    private static final Logger LOG = Logger.getLogger(WanakuCamelCodeExecutionEngineReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<WanakuCamelCodeExecutionEngine> reconcile(
            WanakuCamelCodeExecutionEngine resource, Context<WanakuCamelCodeExecutionEngine> context) throws Exception {

        LOG.infof(
                "Starting code execution engine reconciliation for %s",
                resource.getMetadata().getName());

        ValidateSpecResult validation = validateSpec(resource);
        if (!validation.valid) {
            return setErrorStatus(resource, "ValidationError", validation.errorMessage);
        }

        final String namespace = resource.getMetadata().getNamespace();
        final boolean remote = OperatorUtil.isRemoteDeploymentMode(resource.getSpec());
        final String serviceName = resource.getMetadata().getName();
        final String routerRef = resource.getSpec().getRouterRef();

        WanakuRouter router = kubernetesClient
                .resources(WanakuRouter.class)
                .inNamespace(namespace)
                .withName(routerRef)
                .get();

        if (router == null) {
            return setErrorStatus(
                    resource,
                    "ValidationError",
                    String.format(
                            "Referenced WanakuRouter '%s' not found in namespace '%s'. "
                                    + "Ensure the WanakuRouter resource is created before the "
                                    + "WanakuCamelCodeExecutionEngine.",
                            routerRef, namespace));
        }

        final String serviceUrl = buildServiceUrl(resource);
        final Service desiredService = makeCodeExecutionEngineInternalService(resource);

        if (!remote) {
            final Deployment desiredDeployment = makeDesiredCamelCodeExecutionEngineDeployment(resource, context);
            Deployment existingDeployment = kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();

            if (!match(desiredDeployment, existingDeployment)) {
                LOG.infof("Creating or updating Deployment %s in %s", serviceName, namespace);
                kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .resource(desiredDeployment)
                        .createOr(Replaceable::update);
            }
        }

        Service existingService = kubernetesClient
                .services()
                .inNamespace(namespace)
                .withName(serviceName)
                .get();

        if (!match(desiredService, existingService)) {
            LOG.infof("Creating or updating Service %s in %s", serviceName, namespace);
            kubernetesClient
                    .services()
                    .inNamespace(namespace)
                    .resource(desiredService)
                    .createOr(Replaceable::update);
        }

        final WanakuCamelCodeExecutionEngineStatus status = new WanakuCamelCodeExecutionEngineStatus();
        status.setDeploymentState(remote ? "REMOTE_READY" : "IN_CLUSTER_READY");
        status.setServiceUrl(serviceUrl);
        status.setActiveRoutes(List.of(buildRouteIdentifier(resource)));
        status.setHealthChecks(List.of(buildHealthCheck(resource, remote, serviceName, namespace)));

        final Condition previousReadyCondition = OperatorUtil.findCondition(
                resource.getStatus() != null ? resource.getStatus().getConditions() : null,
                OperatorUtil.READY_CONDITION);

        status.setConditions(List.of(OperatorUtil.readyCondition(
                resource.getMetadata().getGeneration(),
                previousReadyCondition,
                "Camel Code Execution Engine is ready")));

        resource.setStatus(status);
        return UpdateControl.patchStatus(resource);
    }

    private static String buildRouteIdentifier(WanakuCamelCodeExecutionEngine resource) {
        return String.format(
                Locale.ROOT,
                "%s/%s",
                resource.getSpec().getEngineType(),
                resource.getSpec().getLanguageName());
    }

    private WanakuCamelCodeExecutionEngineStatus.HealthCheck buildHealthCheck(
            WanakuCamelCodeExecutionEngine resource, boolean remote, String serviceName, String namespace) {

        WanakuCamelCodeExecutionEngineStatus.HealthCheck healthCheck =
                new WanakuCamelCodeExecutionEngineStatus.HealthCheck();
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

        boolean healthy = deployment != null
                && deployment.getStatus() != null
                && deployment.getStatus().getReadyReplicas() != null
                && deployment.getStatus().getReadyReplicas() > 0;

        healthCheck.setStatus(healthy ? "True" : "False");
        healthCheck.setMessage(
                healthy
                        ? "In-cluster deployment has ready pods ("
                                + deployment.getStatus().getReadyReplicas()
                                + ")"
                        : "In-cluster deployment has not been observed yet");
        return healthCheck;
    }

    private String buildServiceUrl(WanakuCamelCodeExecutionEngine resource) {
        String scheme = resource.getSpec().getRemote() != null
                        && resource.getSpec().getRemote().getScheme() != null
                ? resource.getSpec().getRemote().getScheme()
                : "http";

        Integer port = resource.getSpec().getPort() != null ? resource.getSpec().getPort() : 9190;

        if (OperatorUtil.isRemoteDeploymentMode(resource.getSpec())) {
            String path = resource.getSpec().getRemote().getPath();
            String normalizedPath = path != null && !path.isBlank() ? (path.startsWith("/") ? path : "/" + path) : "";
            Integer remotePort = resource.getSpec().getRemote().getPort() != null
                    ? resource.getSpec().getRemote().getPort()
                    : port;

            return String.format(
                    Locale.ROOT,
                    "%s://%s:%d%s",
                    scheme,
                    resource.getSpec().getRemote().getHost(),
                    remotePort,
                    normalizedPath);
        }

        return String.format(
                Locale.ROOT,
                "http://%s.%s.svc.cluster.local:%d",
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace(),
                port);
    }

    static class ValidateSpecResult {
        boolean valid;
        String errorMessage;

        ValidateSpecResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
    }

    ValidateSpecResult validateSpec(WanakuCamelCodeExecutionEngine resource) {
        final WanakuCamelCodeExecutionEngineSpec spec = resource.getSpec();

        if (spec == null) {
            return new ValidateSpecResult(false, "spec must be provided");
        }
        if (spec.getRouterRef() == null || spec.getRouterRef().isBlank()) {
            return new ValidateSpecResult(false, "routerRef must be specified for the Camel Code Execution Engine");
        }
        if (spec.getLanguageName() == null || spec.getLanguageName().isBlank()) {
            return new ValidateSpecResult(false, "languageName must be specified for the Camel Code Execution Engine");
        }
        if (spec.getEngineType() == null || spec.getEngineType().isBlank()) {
            return new ValidateSpecResult(false, "engineType must be specified for the Camel Code Execution Engine");
        }
        try {
            OperatorUtil.validateDeploymentMode(spec.getDeploymentMode());
        } catch (WanakuException e) {
            return new ValidateSpecResult(false, e.getMessage());
        }

        if (OperatorUtil.isRemoteDeploymentMode(resource.getSpec())) {
            if (spec.getRemote() == null
                    || spec.getRemote().getHost() == null
                    || spec.getRemote().getHost().isBlank()) {
                return new ValidateSpecResult(false, "remote.host must be specified when deploymentMode=remote");
            }
        } else if (spec.getImage() == null || spec.getImage().isBlank()) {
            return new ValidateSpecResult(false, "image must be specified when deploymentMode=in-cluster");
        }

        try {
            validateSecurityLists(spec.getSecurity());
            OperatorUtil.validateCacheStrategy(spec.getDependencyCache());
        } catch (WanakuException e) {
            return new ValidateSpecResult(false, e.getMessage());
        }
        return new ValidateSpecResult(true, null);
    }

    void validateSecurityLists(WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec) {
        if (securitySpec == null) {
            return;
        }
        validateNoOverlap("component", securitySpec.getComponentAllowlist(), securitySpec.getComponentBlocklist());
        validateNoOverlap("endpoint", securitySpec.getEndpointAllowlist(), securitySpec.getEndpointBlocklist());
        validateNoOverlap("route", securitySpec.getRouteAllowlist(), securitySpec.getRouteBlocklist());
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

    private UpdateControl<WanakuCamelCodeExecutionEngine> setErrorStatus(
            WanakuCamelCodeExecutionEngine resource, String reason, String message) {
        LOG.warnf(
                "WanakuCamelCodeExecutionEngine '%s' error (%s): %s",
                resource.getMetadata().getName(), reason, message);

        WanakuCamelCodeExecutionEngineStatus status = new WanakuCamelCodeExecutionEngineStatus();
        Condition condition = new ConditionBuilder()
                .withType(OperatorUtil.READY_CONDITION)
                .withStatus("False")
                .withObservedGeneration(resource.getMetadata().getGeneration())
                .withLastTransitionTime(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .withReason(reason)
                .withMessage(message)
                .build();

        status.setConditions(List.of(condition));
        resource.setStatus(status);
        return UpdateControl.patchStatus(resource);
    }
}
