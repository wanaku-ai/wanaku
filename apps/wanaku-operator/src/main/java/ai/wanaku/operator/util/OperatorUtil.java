package ai.wanaku.operator.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineReconciler;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineSpec;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Shared operator utilities for condition management, image pull policy resolution,
 * auth realm resolution, and router URL construction.
 *
 * <p>Resource-specific factory methods have been extracted to:
 * <ul>
 *   <li>{@link RouterResourceFactory} for router K8s resources</li>
 *   <li>{@link CapabilityResourceFactory} for capability K8s resources</li>
 *   <li>{@link EnvironmentVariableHelper} for environment variable computation</li>
 * </ul>
 */
public final class OperatorUtil {
    private static final Logger LOG = Logger.getLogger(OperatorUtil.class);
    public static final String READY_CONDITION = "Ready";
    public static final String CONDITION_STATUS_TRUE = "True";
    public static final String CONDITION_REASON_READY = "ReconciliationSucceeded";
    public static final String ROUTER_BACKEND_DEPLOYMENT_FILE = "wanaku-router-deployment.yaml";
    public static final String ROUTER_BACKEND_INTERNAL_SERVICE_FILE = "wanaku-router-service-internal.yaml";
    public static final String ROUTER_BACKEND_EXTERNAL_SERVICE_FILE = "wanaku-router-service-external.yaml";
    public static final String ROUTER_INGRESS_FILE = "wanaku-router-ingress.yaml";
    public static final String WANAKU_CAPABILITY_DEPLOYMENT_FILE = "wanaku-capability-deployment.yaml";
    public static final String CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE =
            "camel-integration-capability-deployment.yaml";
    public static final String CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_FILE =
            "camel-code-execution-engine-deployment.yaml";
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String CODE_EXECUTION_ENGINE_INTERNAL_SERVICE_FILE =
            "camel-code-execution-engine-service-internal.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";
    public static final String ROUTER_VOLUME_CLAIM = "router-volume-claim";

    public static final String DEFAULT_PULL_POLICY = "IfNotPresent";
    public static final Set<String> VALID_PULL_POLICIES = Set.of("Always", "IfNotPresent", "Never");

    private OperatorUtil() {}

    /**
     * Creates a Ready condition for a custom resource status.
     *
     * @param generation the observed generation
     * @param previousCondition the previous condition (may be null)
     * @param message the status message
     * @return a new Ready condition
     */
    public static Condition readyCondition(Long generation, Condition previousCondition, String message) {
        final boolean alreadyReady =
                previousCondition != null && CONDITION_STATUS_TRUE.equals(previousCondition.getStatus());
        final String lastTransitionTime = alreadyReady && previousCondition.getLastTransitionTime() != null
                ? previousCondition.getLastTransitionTime()
                : OffsetDateTime.now(ZoneOffset.UTC).toString();

        return new ConditionBuilder()
                .withType(READY_CONDITION)
                .withStatus(CONDITION_STATUS_TRUE)
                .withObservedGeneration(generation)
                .withLastTransitionTime(lastTransitionTime)
                .withReason(CONDITION_REASON_READY)
                .withMessage(message)
                .build();
    }

    /**
     * Finds a condition by type in a list of conditions.
     *
     * @param conditions the list of conditions (may be null or empty)
     * @param type the condition type to find
     * @return the matching condition, or null if not found
     */
    public static Condition findCondition(List<Condition> conditions, String type) {
        if (conditions == null || conditions.isEmpty() || type == null) {
            return null;
        }

        return conditions.stream()
                .filter(condition -> type.equals(condition.getType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves image pull policy with priority:
     * 1. Component-specific policy (router.imagePullPolicy or capability.imagePullPolicy)
     * 2. Global policy (spec.imagePullPolicy)
     * 3. Default (IfNotPresent)
     *
     * @param componentPolicy the component-specific policy (may be null)
     * @param globalPolicy the global policy from spec (may be null)
     * @return the resolved policy, validated against allowed values
     */
    public static String resolveImagePullPolicy(String componentPolicy, String globalPolicy) {
        String resolved =
                componentPolicy != null ? componentPolicy : (globalPolicy != null ? globalPolicy : DEFAULT_PULL_POLICY);

        if (!VALID_PULL_POLICIES.contains(resolved)) {
            LOG.warnf("Invalid imagePullPolicy '%s', using default '%s'", resolved, DEFAULT_PULL_POLICY);
            return DEFAULT_PULL_POLICY;
        }

        return resolved;
    }

    /**
     * Constructs the internal base URL for a router.
     *
     * @param routerRef the router reference name
     * @return the base URL in the format "http://internal-{routerRef}:8080"
     */
    public static String getRouterBaseUrl(String routerRef) {
        return "http://internal-" + routerRef + ":8080";
    }

    static String getInternalRegistrationUri(String routerRef) {
        return getRouterBaseUrl(routerRef) + "/";
    }

    // ---- Code execution engine methods ----

    public static Deployment makeDesiredCamelCodeExecutionEngineDeployment(
            WanakuCodeExecutionEngine resource, Context<WanakuCodeExecutionEngine> context) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class,
                WanakuCodeExecutionEngineReconciler.class,
                CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_FILE);

        return configureCodeExecutionDeployment(desiredDeployment, resource);
    }

    public static Service makeCodeExecutionEngineInternalService(WanakuCodeExecutionEngine resource) {
        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class, WanakuCodeExecutionEngineReconciler.class, CODE_EXECUTION_ENGINE_INTERNAL_SERVICE_FILE);

        final String serviceName = resource.getMetadata().getName();
        final String ns = resource.getMetadata().getNamespace();
        final int port = resolveCodeExecutionPort(resource);

        service.getMetadata().setName(serviceName);
        service.getMetadata().setNamespace(ns);
        service.getMetadata().getLabels().put("app", serviceName);
        service.getMetadata().getLabels().put("component", "camel-code-execution-engine");
        service.getMetadata().getLabels().put("serviceType", "code-execution-engine");
        service.getMetadata()
                .getLabels()
                .put("serviceSubType", resource.getSpec().getEngineType());
        service.getMetadata().getLabels().put("languageName", resource.getSpec().getLanguageName());

        if (isRemoteDeploymentMode(resource.getSpec())) {
            String remoteHost = resource.getSpec().getRemote().getHost();
            String scheme = resource.getSpec().getRemote().getScheme() != null
                    ? resource.getSpec().getRemote().getScheme()
                    : "http";
            service.getSpec().setType("ExternalName");
            service.getSpec().setExternalName(remoteHost);
            service.getSpec().getPorts().clear();
            if (service.getSpec().getSelector() != null) {
                service.getSpec().getSelector().clear();
            }
            service.getMetadata().getAnnotations().put("wanaku.ai/remote-scheme", scheme);
        } else {
            service.getSpec().getPorts().getFirst().setPort(port);
            service.getSpec().getPorts().getFirst().setTargetPort(new IntOrString(port));
            service.getSpec()
                    .setSelector(Map.of(
                            "app", serviceName,
                            "component", "camel-code-execution-engine",
                            "serviceType", "code-execution-engine"));
        }

        service.addOwnerReference(resource);
        return service;
    }

    private static Deployment configureCodeExecutionDeployment(
            Deployment desiredDeployment, WanakuCodeExecutionEngine resource) {
        String serviceName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        desiredDeployment.getMetadata().setName(serviceName);
        desiredDeployment.getMetadata().setNamespace(ns);
        desiredDeployment.getMetadata().getLabels().put("app", serviceName);
        desiredDeployment.getMetadata().getLabels().put("component", "camel-code-execution-engine");
        desiredDeployment.getMetadata().getLabels().put("serviceType", "code-execution-engine");
        desiredDeployment
                .getMetadata()
                .getLabels()
                .put("serviceSubType", resource.getSpec().getEngineType());
        desiredDeployment
                .getMetadata()
                .getLabels()
                .put("languageName", resource.getSpec().getLanguageName());

        final DeploymentSpec deploymentSpec = desiredDeployment.getSpec();
        deploymentSpec.getSelector().getMatchLabels().put("app", serviceName);
        deploymentSpec.getSelector().getMatchLabels().put("component", "camel-code-execution-engine");
        deploymentSpec.getTemplate().getMetadata().getLabels().put("app", serviceName);
        deploymentSpec.getTemplate().getMetadata().getLabels().put("component", "camel-code-execution-engine");
        deploymentSpec.getTemplate().getMetadata().getLabels().put("serviceType", "code-execution-engine");
        deploymentSpec
                .getTemplate()
                .getMetadata()
                .getLabels()
                .put("serviceSubType", resource.getSpec().getEngineType());
        deploymentSpec
                .getTemplate()
                .getMetadata()
                .getLabels()
                .put("languageName", resource.getSpec().getLanguageName());

        final Container container =
                deploymentSpec.getTemplate().getSpec().getContainers().getFirst();
        container.setName(serviceName);
        container.setImage(resource.getSpec().getImage());
        container.setImagePullPolicy(resolveImagePullPolicy(resource.getSpec().getImagePullPolicy(), null));
        container.setEnv(computeCodeExecutionEngineEnvVars(resource));

        final Integer port = resolveCodeExecutionPort(resource);
        container.getPorts().getFirst().setContainerPort(port);
        container.getPorts().getFirst().setName("grpc");

        if (container.getLivenessProbe() != null && container.getLivenessProbe().getTcpSocket() != null) {
            container.getLivenessProbe().getTcpSocket().setPort(new IntOrString(port));
        }
        if (container.getReadinessProbe() != null
                && container.getReadinessProbe().getTcpSocket() != null) {
            container.getReadinessProbe().getTcpSocket().setPort(new IntOrString(port));
        }

        applyResourceRequirements(container, resource.getSpec().getResources());

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    private static void applyResourceRequirements(
            Container container, WanakuCodeExecutionEngineSpec.ResourceSpec resourceSpec) {
        if (resourceSpec == null) {
            return;
        }

        ResourceRequirements requirements = new ResourceRequirements();
        Map<String, Quantity> requests = new HashMap<>();
        Map<String, Quantity> limits = new HashMap<>();

        if (resourceSpec.getCpuRequest() != null
                && !resourceSpec.getCpuRequest().isBlank()) {
            requests.put("cpu", new Quantity(resourceSpec.getCpuRequest()));
        }
        if (resourceSpec.getMemoryRequest() != null
                && !resourceSpec.getMemoryRequest().isBlank()) {
            requests.put("memory", new Quantity(resourceSpec.getMemoryRequest()));
        }
        if (resourceSpec.getCpuLimit() != null && !resourceSpec.getCpuLimit().isBlank()) {
            limits.put("cpu", new Quantity(resourceSpec.getCpuLimit()));
        }
        if (resourceSpec.getMemoryLimit() != null
                && !resourceSpec.getMemoryLimit().isBlank()) {
            limits.put("memory", new Quantity(resourceSpec.getMemoryLimit()));
        }

        if (!requests.isEmpty()) {
            requirements.setRequests(requests);
        }
        if (!limits.isEmpty()) {
            requirements.setLimits(limits);
        }

        if (!requests.isEmpty() || !limits.isEmpty()) {
            container.setResources(requirements);
        }
    }

    private static List<EnvVar> computeCodeExecutionEngineEnvVars(WanakuCodeExecutionEngine resource) {
        List<EnvVar> envVars = new ArrayList<>();
        final String serviceName = resource.getMetadata().getName();
        final String registrationUri =
                getInternalRegistrationUri(resource.getSpec().getRouterRef());
        final String authServer = resource.getSpec().getAuth() != null
                ? resource.getSpec().getAuth().getAuthServer()
                : null;
        final String oidcSecret = resource.getSpec().getSecrets() != null
                ? resource.getSpec().getSecrets().getOidcCredentialsSecret()
                : null;

        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_SERVICE_NAME)
                .withValue(serviceName)
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.WANAKU_SERVICE_REGISTRATION_URI)
                .withValue(registrationUri)
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_MODE)
                .withValue(normalizeDeploymentMode(resource.getSpec().getDeploymentMode()))
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_ENGINE_TYPE)
                .withValue(resource.getSpec().getEngineType())
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_LANGUAGE_NAME)
                .withValue(resource.getSpec().getLanguageName())
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_GRPC_PORT)
                .withValue(String.valueOf(
                        resource.getSpec().getPort() != null
                                ? resource.getSpec().getPort()
                                : 9190))
                .build());

        if (authServer != null && !authServer.isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT)
                    .withValue(authServer + "/realms/wanaku")
                    .build());
        }
        if (oidcSecret != null && !oidcSecret.isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET)
                    .withValue(oidcSecret)
                    .build());
        }

        addSecurityEnvVars(resource.getSpec().getSecurity(), envVars);
        addCacheEnvVars(resource.getSpec().getDependencyCache(), envVars);
        addRemoteEnvVars(resource.getSpec().getRemote(), envVars);
        EnvironmentVariableHelper.addCustomVars(resource.getSpec().getEnv(), envVars);
        return envVars;
    }

    private static void addSecurityEnvVars(
            WanakuCodeExecutionEngineSpec.SecuritySpec securitySpec, List<EnvVar> envVars) {
        if (securitySpec == null) {
            return;
        }

        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_COMPONENT_ALLOWLIST,
                securitySpec.getComponentAllowlist(),
                envVars);
        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_COMPONENT_BLOCKLIST,
                securitySpec.getComponentBlocklist(),
                envVars);
        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_ENDPOINT_ALLOWLIST,
                securitySpec.getEndpointAllowlist(),
                envVars);
        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_ENDPOINT_BLOCKLIST,
                securitySpec.getEndpointBlocklist(),
                envVars);
        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_ROUTE_ALLOWLIST,
                securitySpec.getRouteAllowlist(),
                envVars);
        addCommaSeparatedEnvVar(
                EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_ROUTE_BLOCKLIST,
                securitySpec.getRouteBlocklist(),
                envVars);
    }

    private static void addCacheEnvVars(
            WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec, List<EnvVar> envVars) {
        if (cacheSpec == null) {
            return;
        }

        if (cacheSpec.getEnabled() != null) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_STRATEGY)
                    .withValue(Boolean.TRUE.equals(cacheSpec.getEnabled()) ? cacheSpec.getStrategy() : "disabled")
                    .build());
        } else if (cacheSpec.getStrategy() != null) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_STRATEGY)
                    .withValue(cacheSpec.getStrategy())
                    .build());
        }

        if (cacheSpec.getCacheName() != null && !cacheSpec.getCacheName().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_NAME)
                    .withValue(cacheSpec.getCacheName())
                    .build());
        }
        if (cacheSpec.getTemplateNamespace() != null
                && !cacheSpec.getTemplateNamespace().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_TEMPLATE_NAMESPACE)
                    .withValue(cacheSpec.getTemplateNamespace())
                    .build());
        }
        if (cacheSpec.getTemplatePrefix() != null
                && !cacheSpec.getTemplatePrefix().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_TEMPLATE_PREFIX)
                    .withValue(cacheSpec.getTemplatePrefix())
                    .build());
        }
    }

    private static void addRemoteEnvVars(WanakuCodeExecutionEngineSpec.RemoteSpec remoteSpec, List<EnvVar> envVars) {
        if (remoteSpec == null) {
            return;
        }

        if (remoteSpec.getHost() != null && !remoteSpec.getHost().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_REMOTE_HOST)
                    .withValue(remoteSpec.getHost())
                    .build());
        }
        if (remoteSpec.getPort() != null) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_REMOTE_PORT)
                    .withValue(String.valueOf(remoteSpec.getPort()))
                    .build());
        }
        if (remoteSpec.getScheme() != null && !remoteSpec.getScheme().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_REMOTE_SCHEME)
                    .withValue(remoteSpec.getScheme())
                    .build());
        }
        if (remoteSpec.getPath() != null && !remoteSpec.getPath().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_REMOTE_PATH)
                    .withValue(remoteSpec.getPath())
                    .build());
        }
    }

    private static void addCommaSeparatedEnvVar(String name, List<String> values, List<EnvVar> envVars) {
        if (values == null || values.isEmpty()) {
            return;
        }

        envVars.add(new EnvVarBuilder()
                .withName(name)
                .withValue(String.join(",", values))
                .build());
    }

    public static boolean isRemoteDeploymentMode(WanakuCodeExecutionEngineSpec spec) {
        return "remote".equalsIgnoreCase(normalizeDeploymentMode(spec.getDeploymentMode()));
    }

    public static int resolveCodeExecutionPort(WanakuCodeExecutionEngine resource) {
        if (isRemoteDeploymentMode(resource.getSpec())
                && resource.getSpec().getRemote() != null
                && resource.getSpec().getRemote().getPort() != null) {
            return resource.getSpec().getRemote().getPort();
        }

        return resource.getSpec().getPort() != null ? resource.getSpec().getPort() : 9190;
    }

    public static String normalizeDeploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
        }

        String normalized = deploymentMode.trim().toLowerCase();
        if (WanakuTypes.VALID_DEPLOYMENT_MODES.contains(normalized) || "incluster".equals(normalized)) {
            return WanakuTypes.DEPLOYMENT_MODE_REMOTE.equals(normalized)
                    ? WanakuTypes.DEPLOYMENT_MODE_REMOTE
                    : WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
        }
        return WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
    }

    public static void validateDeploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return;
        }
        String normalized = deploymentMode.trim().toLowerCase();
        if (!WanakuTypes.VALID_DEPLOYMENT_MODES.contains(normalized) && !"incluster".equals(normalized)) {
            throw new WanakuException(
                    "deploymentMode must be one of: " + String.join(", ", WanakuTypes.VALID_DEPLOYMENT_MODES));
        }
    }

    public static void validateCacheStrategy(WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec) {
        if (cacheSpec == null
                || cacheSpec.getStrategy() == null
                || cacheSpec.getStrategy().isBlank()) {
            return;
        }

        String strategy = cacheSpec.getStrategy().trim().toLowerCase();
        if (!WanakuTypes.VALID_CACHE_STRATEGIES.contains(strategy)) {
            throw new WanakuException("dependencyCache.strategy must be one of: "
                    + String.join(", ", WanakuTypes.VALID_CACHE_STRATEGIES));
        }
    }

    static String resolveAuthRealm(WanakuCapability resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }

    /**
     * Resolves the auth realm for a WanakuRouter resource.
     *
     * @param resource the WanakuRouter custom resource (may be null)
     * @return the configured realm, or the default "wanaku" realm
     */
    static String resolveAuthRealm(WanakuRouter resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }
}
