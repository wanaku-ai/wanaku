package ai.wanaku.operator.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
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
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineReconciler;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineSpec;
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
    public static final String CAMEL_CODE_EXECUTION_ENGINE_DEPLOYMENT_FILE =
            "camel-code-execution-engine-deployment.yaml";
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String CODE_EXECUTION_ENGINE_INTERNAL_SERVICE_FILE =
            "camel-code-execution-engine-service-internal.yaml";
    public static final String CODE_EXECUTION_ENGINE_ENDPOINTS_FILE = "camel-code-execution-engine-endpoints.yaml";
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

        String realm = resolveAuthRealm(resource);

        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_SERVER)
                .withValue(authServer)
                .build();
        EnvVar authProxyEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_PROXY)
                .withValue(authProxy)
                .build();
        EnvVar authRealmEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_REALM)
                .withValue(realm)
                .build();

        List<EnvVar> envVars = new java.util.ArrayList<>();
        envVars.add(authServerEnv);
        envVars.add(authProxyEnv);
        envVars.add(authRealmEnv);

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

        String realm = resolveAuthRealm(resource);
        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT)
                .withValue(authServer + "/realms/" + realm)
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

        if (capabilitiesSpec.getServiceCatalog() != null
                && !capabilitiesSpec.getServiceCatalog().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG)
                    .withValue(capabilitiesSpec.getServiceCatalog())
                    .build());
        }
        if (capabilitiesSpec.getServiceCatalogSystem() != null
                && !capabilitiesSpec.getServiceCatalogSystem().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG_SYSTEM)
                    .withValue(capabilitiesSpec.getServiceCatalogSystem())
                    .build());
        }

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
        service.getSpec().getPorts().getFirst().setPort(port);
        service.getSpec().getPorts().getFirst().setTargetPort(new IntOrString(port));

        if (isRemoteDeploymentMode(resource.getSpec())) {
            if (service.getSpec().getSelector() != null) {
                service.getSpec().getSelector().clear();
            }
        } else {
            service.getSpec()
                    .setSelector(Map.of(
                            "app", serviceName,
                            "component", "camel-code-execution-engine",
                            "serviceType", "code-execution-engine"));
        }

        service.addOwnerReference(resource);
        return service;
    }

    public static Endpoints makeCodeExecutionEngineEndpoints(WanakuCodeExecutionEngine resource) {
        Endpoints endpoints = ReconcilerUtilsInternal.loadYaml(
                Endpoints.class, WanakuCodeExecutionEngineReconciler.class, CODE_EXECUTION_ENGINE_ENDPOINTS_FILE);

        final String serviceName = resource.getMetadata().getName();
        final String ns = resource.getMetadata().getNamespace();
        final int port = resolveCodeExecutionPort(resource);

        endpoints.getMetadata().setName(serviceName);
        endpoints.getMetadata().setNamespace(ns);
        endpoints.getMetadata().getLabels().put("app", serviceName);
        endpoints.getMetadata().getLabels().put("component", "camel-code-execution-engine");
        endpoints.getMetadata().getLabels().put("serviceType", "code-execution-engine");
        endpoints
                .getMetadata()
                .getLabels()
                .put("serviceSubType", resource.getSpec().getEngineType());
        endpoints
                .getMetadata()
                .getLabels()
                .put("languageName", resource.getSpec().getLanguageName());

        final EndpointAddress endpointAddress = new EndpointAddress();
        endpointAddress.setHostname(resource.getSpec().getRemote().getHost());

        final EndpointPort endpointPort = new EndpointPort();
        endpointPort.setPort(port);
        endpointPort.setProtocol("TCP");
        endpointPort.setName("grpc");

        final EndpointSubset subset = new EndpointSubset();
        subset.setAddresses(List.of(endpointAddress));
        subset.setPorts(List.of(endpointPort));
        endpoints.setSubsets(List.of(subset));

        endpoints.addOwnerReference(resource);
        return endpoints;
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
        container.setImagePullPolicy(resolveImagePullPolicy(
                resource.getSpec().getImagePullPolicy(), resource.getSpec().getImagePullPolicy()));
        container.setEnv(computeCodeExecutionEngineEnvVars(resource));

        final Integer port = resolveCodeExecutionPort(resource);
        container.getPorts().getFirst().setContainerPort(port);
        container.getPorts().getFirst().setName("grpc");

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
        Map<String, Quantity> requests = new java.util.HashMap<>();
        Map<String, Quantity> limits = new java.util.HashMap<>();

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
        addCustomVars(resource.getSpec().getEnv(), envVars);
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

    private static boolean isRemoteDeploymentMode(WanakuCodeExecutionEngineSpec spec) {
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

    private static String normalizeDeploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return "in-cluster";
        }

        String normalized = deploymentMode.trim().toLowerCase();
        if ("remote".equals(normalized) || "in-cluster".equals(normalized) || "incluster".equals(normalized)) {
            return "remote".equals(normalized) ? "remote" : "in-cluster";
        }
        return "in-cluster";
    }

    private static String getInternalRegistrationUri(String routerRef) {
        return "http://internal-" + routerRef + ":8080/";
    }

    static String resolveAuthRealm(WanakuCapability resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }

    static String resolveAuthRealm(WanakuRouter resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }
}
