package ai.wanaku.operator.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
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
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngineReconciler;
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngineSpec;

/**
 * Factory methods for Kubernetes resources belonging to a {@link WanakuCamelCodeExecutionEngine}.
 *
 * <p>Extracted from {@link OperatorUtil} following the pattern established by
 * {@link RouterResourceFactory} and {@link CapabilityResourceFactory}.
 */
public final class CodeExecutionEngineResourceFactory {

    private static final Logger LOG = Logger.getLogger(CodeExecutionEngineResourceFactory.class);

    public static final String CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_FILE =
            "camel-code-execution-engine-deployment.yaml";
    public static final String CODE_EXECUTION_ENGINE_INTERNAL_SERVICE_FILE =
            "camel-code-execution-engine-service-internal.yaml";

    private CodeExecutionEngineResourceFactory() {}

    public static Deployment makeDesiredCamelCodeExecutionEngineDeployment(
            WanakuCamelCodeExecutionEngine resource, Context<WanakuCamelCodeExecutionEngine> context) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class,
                WanakuCamelCodeExecutionEngineReconciler.class,
                CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_FILE);
        return configureCodeExecutionDeployment(desiredDeployment, resource);
    }

    public static Service makeCodeExecutionEngineInternalService(WanakuCamelCodeExecutionEngine resource) {
        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class,
                WanakuCamelCodeExecutionEngineReconciler.class,
                CODE_EXECUTION_ENGINE_INTERNAL_SERVICE_FILE);
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
            if (service.getMetadata().getAnnotations() == null) {
                service.getMetadata().setAnnotations(new java.util.HashMap<>());
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
            Deployment desiredDeployment, WanakuCamelCodeExecutionEngine resource) {
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
        OperatorUtil.validateImageAllowed(resource.getSpec().getImage());
        container.setImage(resource.getSpec().getImage());
        container.setImagePullPolicy(
                OperatorUtil.resolveImagePullPolicy(resource.getSpec().getImagePullPolicy(), null));
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
            Container container, WanakuCamelCodeExecutionEngineSpec.ResourceSpec resourceSpec) {
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

    private static List<EnvVar> computeCodeExecutionEngineEnvVars(WanakuCamelCodeExecutionEngine resource) {
        List<EnvVar> envVars = new ArrayList<>();
        final String serviceName = resource.getMetadata().getName();
        final String registrationUri =
                OperatorUtil.getInternalRegistrationUri(resource.getSpec().getRouterRef());
        final String authRealm = OperatorUtil.resolveAuthRealm(resource);
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
                .withValue(
                        OperatorUtil.normalizeDeploymentMode(resource.getSpec().getDeploymentMode()))
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
                    .withValue(authServer + "/realms/" + authRealm)
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
            WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec, List<EnvVar> envVars) {
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
            WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec, List<EnvVar> envVars) {
        if (cacheSpec == null) {
            return;
        }
        if (cacheSpec.getEnabled() != null) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_CODE_EXECUTION_ENGINE_DEPENDENCY_CACHE_STRATEGY)
                    .withValue(
                            Boolean.TRUE.equals(cacheSpec.getEnabled())
                                    ? (cacheSpec.getStrategy() != null
                                                    && !cacheSpec.getStrategy().isBlank()
                                            ? cacheSpec.getStrategy()
                                            : "inmemory")
                                    : "disabled")
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

    private static void addRemoteEnvVars(
            WanakuCamelCodeExecutionEngineSpec.RemoteSpec remoteSpec, List<EnvVar> envVars) {
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

    public static boolean isRemoteDeploymentMode(WanakuCamelCodeExecutionEngineSpec spec) {
        return OperatorUtil.isRemoteDeploymentMode(spec);
    }

    public static int resolveCodeExecutionPort(WanakuCamelCodeExecutionEngine resource) {
        if (isRemoteDeploymentMode(resource.getSpec())
                && resource.getSpec().getRemote() != null
                && resource.getSpec().getRemote().getPort() != null) {
            return resource.getSpec().getRemote().getPort();
        }
        return resource.getSpec().getPort() != null ? resource.getSpec().getPort() : 9190;
    }

    public static Condition readyCondition(Long generation, Condition previousCondition, String message) {
        return OperatorUtil.readyCondition(generation, previousCondition, message);
    }

    public static Condition findCondition(List<Condition> conditions, String type) {
        return OperatorUtil.findCondition(conditions, type);
    }
}
