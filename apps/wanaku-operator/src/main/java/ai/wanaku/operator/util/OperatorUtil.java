package ai.wanaku.operator.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCapabilityReconciler;
import ai.wanaku.operator.wanaku.WanakuCapabilitySpec;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterReconciler;
import ai.wanaku.operator.wanaku.WanakuRouterSpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

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
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";
    public static final String ROUTER_VOLUME_CLAIM = "router-volume-claim";

    public static final String DEFAULT_PULL_POLICY = "IfNotPresent";
    public static final Set<String> VALID_PULL_POLICIES = Set.of("Always", "IfNotPresent", "Never");

    private OperatorUtil() {}

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

    // ---- Router methods ----

    private static void setupBackendContainer(WanakuRouter resource, DeploymentSpec spec, String host) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.stream()
                .filter(c -> c.getName().equals("wanaku-mcp-router"))
                .findFirst()
                .get();

        final String authServer = resource.getSpec().getAuth().getAuthServer();

        // If a proxy is not defined,
        String authProxy = resource.getSpec().getAuth().getAuthProxy();
        if ("auto".equals(authProxy)) {
            // TODO: needs to support https
            authProxy = String.format("http://%s", host);
        } else {
            if (authProxy == null) {
                authProxy = authServer;
            }
        }

        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_SERVER)
                .withValue(authServer)
                .build();
        EnvVar authProxyEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_PROXY)
                .withValue(authProxy)
                .build();

        List<EnvVar> envVars = new java.util.ArrayList<>();
        envVars.add(authServerEnv);
        envVars.add(authProxyEnv);

        final WanakuRouterSpec.RouterSpec routerSpec = resource.getSpec().getRouter();

        // Resolve pull policy with fallback chain: component -> global -> default
        String componentPolicy = routerSpec != null ? routerSpec.getImagePullPolicy() : null;
        String globalPolicy = resource.getSpec().getImagePullPolicy();
        String resolvedPolicy = resolveImagePullPolicy(componentPolicy, globalPolicy);
        service.setImagePullPolicy(resolvedPolicy);

        if (routerSpec != null) {
            // Set a custom image
            final String image = routerSpec.getImage();

            if (image != null) {
                service.setImage(image);
            }

            // Add custom environment variables from router spec if provided
            if (routerSpec.getEnv() != null && !routerSpec.getEnv().isEmpty()) {
                for (WanakuTypes.EnvVar env : routerSpec.getEnv()) {
                    EnvVar customEnvVar = new EnvVarBuilder()
                            .withName(env.getName())
                            .withValue(env.getValue())
                            .build();
                    envVars.add(customEnvVar);
                }
            }
        }

        service.setEnv(envVars);
    }

    public static Deployment makeDesiredRouterBackendDeployment(
            WanakuRouter resource, Context<WanakuRouter> context, String host) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, WanakuRouterReconciler.class, ROUTER_BACKEND_DEPLOYMENT_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        desiredDeployment.getMetadata().setName(routerName(deploymentName));
        desiredDeployment.getMetadata().setNamespace(ns);

        final DeploymentSpec serviceSpec = desiredDeployment.getSpec();

        serviceSpec.getSelector().getMatchLabels().put("app", routerName(deploymentName));
        serviceSpec.getSelector().getMatchLabels().put("component", "wanaku-router-backend");
        serviceSpec.getTemplate().getMetadata().getLabels().put("app", routerName(deploymentName));
        serviceSpec.getTemplate().getMetadata().getLabels().put("component", "wanaku-router-backend");

        setupBackendContainer(resource, serviceSpec, host);

        final Container routerContainer = serviceSpec.getTemplate().getSpec().getContainers().stream()
                .filter(c -> c.getName().equals("wanaku-mcp-router"))
                .findFirst()
                .get();

        // Override image if specified in router spec
        if (resource.getSpec().getRouter() != null
                && resource.getSpec().getRouter().getImage() != null
                && !resource.getSpec().getRouter().getImage().isEmpty()) {
            routerContainer.setImage(resource.getSpec().getRouter().getImage());
            LOG.infof(
                    "Using custom router image: %s",
                    resource.getSpec().getRouter().getImage());
        }

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    private static String routerName(String deploymentName) {
        return deploymentName + "-mcp-router";
    }

    public static Service makeRouterInternalService(WanakuRouter resource) {
        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class, WanakuRouterReconciler.class, ROUTER_BACKEND_INTERNAL_SERVICE_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating new external service for deployment: %s", deploymentName);
        service.getMetadata().setName("internal-" + deploymentName);
        service.getMetadata().setNamespace(ns);
        service.getMetadata().getLabels().put("app", routerName(deploymentName));
        service.getMetadata().getLabels().put("component", "wanaku-router-backend");
        service.getSpec().getSelector().put("app", routerName(deploymentName));

        ServiceSpec serviceSpec = service.getSpec();
        serviceSpec.setSelector(Map.of("app", routerName(deploymentName), "component", "wanaku-router-backend"));

        service.addOwnerReference(resource);

        return service;
    }

    public static Route makeRouterExternalService(WanakuRouter resource) {
        Route route = ReconcilerUtilsInternal.loadYaml(
                Route.class, WanakuRouterReconciler.class, ROUTER_BACKEND_EXTERNAL_SERVICE_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating new external service for deployment: %s", deploymentName);
        route.getMetadata().setName(deploymentName);
        route.getMetadata().setNamespace(ns);
        route.getMetadata().getLabels().put("app", routerName(deploymentName));
        route.getMetadata().getLabels().put("component", "wanaku-router-backend");
        route.getSpec().getTo().setName("internal-" + deploymentName);

        route.addOwnerReference(resource);

        return route;
    }

    public static Ingress makeRouterIngress(WanakuRouter resource, String host) {
        Ingress ingress =
                ReconcilerUtilsInternal.loadYaml(Ingress.class, WanakuRouterReconciler.class, ROUTER_INGRESS_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating new ingress for deployment: %s", deploymentName);
        ingress.getMetadata().setName(deploymentName);
        ingress.getMetadata().setNamespace(ns);
        ingress.getMetadata().getLabels().put("app", routerName(deploymentName));
        ingress.getMetadata().getLabels().put("component", "wanaku-router-backend");

        // Set the host and backend service
        ingress.getSpec().getRules().getFirst().setHost(host);
        ingress.getSpec()
                .getRules()
                .getFirst()
                .getHttp()
                .getPaths()
                .getFirst()
                .getBackend()
                .getService()
                .setName("internal-" + deploymentName);

        ingress.addOwnerReference(resource);

        return ingress;
    }

    public static PersistentVolumeClaim makeRouterVolumePVC(WanakuRouter resource) {
        PersistentVolumeClaim pvc = ReconcilerUtilsInternal.loadYaml(
                PersistentVolumeClaim.class, WanakuRouterReconciler.class, SERVICES_VOLUME_PVC_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating services-volume PVC for deployment: %s", deploymentName);
        pvc.getMetadata().setName(ROUTER_VOLUME_CLAIM);
        pvc.getMetadata().setNamespace(ns);
        pvc.getMetadata().getLabels().put("app", routerName(deploymentName));
        pvc.getMetadata().getLabels().put("component", "wanaku-router-storage");

        pvc.addOwnerReference(resource);

        return pvc;
    }

    // ---- Capability methods ----

    public static PersistentVolumeClaim makeServicesVolumePVC(WanakuCapability resource, String serviceName) {
        PersistentVolumeClaim pvc = ReconcilerUtilsInternal.loadYaml(
                PersistentVolumeClaim.class, WanakuCapabilityReconciler.class, SERVICES_VOLUME_PVC_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating services-volume PVC for deployment: %s", deploymentName);
        pvc.getMetadata().setName(createVolumeClaimName(serviceName));
        pvc.getMetadata().setNamespace(ns);
        pvc.getMetadata().getLabels().put("app", deploymentName);
        pvc.getMetadata().getLabels().put("component", "wanaku-services-storage");

        pvc.addOwnerReference(resource);

        return pvc;
    }

    public static String createVolumeClaimName(String serviceName) {
        return serviceName + "-volume-claim";
    }

    private static void setupCapabilityContainer(
            WanakuCapability resource,
            DeploymentSpec spec,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec,
            Supplier<List<EnvVar>> envVarSupplier) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.get(0);
        final String serviceName = capabilitiesSpec.getName();
        service.setName(serviceName);

        String serviceImage = capabilitiesSpec.getImage();
        service.setImage(serviceImage);

        // Resolve pull policy with fallback chain: component -> global -> default
        String componentPolicy = capabilitiesSpec.getImagePullPolicy();
        String globalPolicy = resource.getSpec().getImagePullPolicy();
        String resolvedPolicy = resolveImagePullPolicy(componentPolicy, globalPolicy);
        service.setImagePullPolicy(resolvedPolicy);

        final List<EnvVar> userDefinedVars = envVarSupplier.get();
        final List<EnvVar> templateEnvs = service.getEnv();

        for (EnvVar templateVar : templateEnvs) {
            final Optional<EnvVar> override = userDefinedVars.stream()
                    .filter(envVar -> envVar.getName().equals(templateVar.getName()))
                    .findFirst();

            if (override.isEmpty()) {
                userDefinedVars.add(templateVar);
            }
        }

        service.setEnv(userDefinedVars);
    }

    private static List<EnvVar> computeWanakuCapabilitiesEnvVars(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        List<WanakuTypes.EnvVar> customEnv = capabilitiesSpec.getEnv();
        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();

        // Build the registration URI - use the router ref for service discovery
        String registrationUri = getInternalRegistrationUri(resource);

        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_SERVER)
                .withValue(authServer)
                .build();
        EnvVar registrationUriEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.WANAKU_SERVICE_REGISTRATION_URI)
                .withValue(registrationUri)
                .build();
        EnvVar oidcSecretEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET)
                .withValue(oidcSecret)
                .build();

        List<EnvVar> envVars = new java.util.ArrayList<>();
        envVars.add(authServerEnv);
        envVars.add(registrationUriEnv);
        envVars.add(oidcSecretEnv);

        // Add custom environment variables if provided
        addCustomVars(customEnv, envVars);
        return envVars;
    }

    private static List<EnvVar> computeCamelIntegrationCapabilitiesEnvVars(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        List<WanakuTypes.EnvVar> customEnv = capabilitiesSpec.getEnv();
        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();

        // Build the registration URI - use the router ref for service discovery
        String registrationUri = getInternalRegistrationUri(resource);

        // TODO: this one should be made more flexible, so it can accept different realms
        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT)
                .withValue(authServer + "/realms/wanaku")
                .build();
        EnvVar registrationUriEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_URL)
                .withValue(registrationUri)
                .build();
        EnvVar oidcSecretEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET)
                .withValue(oidcSecret)
                .build();

        EnvVar serviceName = new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_NAME)
                .withValue(capabilitiesSpec.getName())
                .build();

        List<EnvVar> envVars = new java.util.ArrayList<>();
        envVars.add(authServerEnv);
        envVars.add(registrationUriEnv);
        envVars.add(oidcSecretEnv);
        envVars.add(serviceName);

        addCustomVars(customEnv, envVars);
        return envVars;
    }

    private static void addCustomVars(List<WanakuTypes.EnvVar> customEnv, List<EnvVar> envVars) {
        // Add custom environment variables if provided
        if (customEnv != null && !customEnv.isEmpty()) {
            for (WanakuTypes.EnvVar env : customEnv) {
                EnvVar customEnvVar = new EnvVarBuilder()
                        .withName(env.getName())
                        .withValue(env.getValue())
                        .build();
                envVars.add(customEnvVar);
            }
        }
    }

    public static String getRouterBaseUrl(String routerRef) {
        return "http://internal-" + routerRef + ":8080";
    }

    private static String getInternalRegistrationUri(WanakuCapability resource) {
        return getRouterBaseUrl(resource.getSpec().getRouterRef()) + "/";
    }

    private static Deployment configureCapabilityDeployment(
            Deployment desiredDeployment,
            WanakuCapability resource,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec,
            Supplier<List<EnvVar>> envVarSupplier) {

        String serviceName = capabilitiesSpec.getName();
        String ns = resource.getMetadata().getNamespace();
        String parentName = resource.getMetadata().getName();

        desiredDeployment.getMetadata().setName(serviceName);
        desiredDeployment.getMetadata().setNamespace(ns);
        desiredDeployment.getMetadata().getLabels().put("app", parentName);
        desiredDeployment.getMetadata().getLabels().put("component", serviceName);

        final DeploymentSpec deploymentSpec = desiredDeployment.getSpec();

        deploymentSpec.getSelector().getMatchLabels().put("app", parentName);
        deploymentSpec.getSelector().getMatchLabels().put("component", serviceName);
        deploymentSpec.getTemplate().getMetadata().getLabels().put("app", parentName);
        deploymentSpec.getTemplate().getMetadata().getLabels().put("component", serviceName);
        deploymentSpec
                .getTemplate()
                .getSpec()
                .getContainers()
                .getFirst()
                .getVolumeMounts()
                .getFirst()
                .setName(serviceName + "-volume");
        deploymentSpec.getTemplate().getSpec().getVolumes().getFirst().setName(serviceName + "-volume");
        deploymentSpec
                .getTemplate()
                .getSpec()
                .getVolumes()
                .getFirst()
                .getPersistentVolumeClaim()
                .setClaimName(createVolumeClaimName(serviceName));

        setupCapabilityContainer(resource, deploymentSpec, capabilitiesSpec, envVarSupplier);

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    public static Deployment makeDesiredWanakuCapabilityDeployment(
            WanakuCapability resource,
            Context<WanakuCapability> context,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, WanakuCapabilityReconciler.class, WANAKU_CAPABILITY_DEPLOYMENT_FILE);

        return configureCapabilityDeployment(
                desiredDeployment,
                resource,
                capabilitiesSpec,
                () -> computeWanakuCapabilitiesEnvVars(resource, capabilitiesSpec));
    }

    public static Deployment makeDesiredCiCCapabilityDeployment(
            WanakuCapability resource,
            Context<WanakuCapability> context,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, WanakuCapabilityReconciler.class, CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE);

        return configureCapabilityDeployment(
                desiredDeployment,
                resource,
                capabilitiesSpec,
                () -> computeCamelIntegrationCapabilitiesEnvVars(resource, capabilitiesSpec));
    }

    public static Service makeCapabilityInternalService(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class, WanakuCapabilityReconciler.class, CAPABILITY_INTERNAL_SERVICE_FILE);

        String serviceName = capabilitiesSpec.getName();
        String ns = resource.getMetadata().getNamespace();
        String parentName = resource.getMetadata().getName();

        LOG.infof("Creating internal service for capability: %s", serviceName);
        service.getMetadata().setName(serviceName);
        service.getMetadata().setNamespace(ns);
        service.getMetadata().getLabels().put("app", parentName);
        service.getMetadata().getLabels().put("component", serviceName);

        ServiceSpec serviceSpec2 = service.getSpec();
        serviceSpec2.setSelector(Map.of("app", parentName, "component", serviceName));

        service.addOwnerReference(resource);

        return service;
    }
}
