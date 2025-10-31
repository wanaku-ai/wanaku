package ai.wanaku.operator.util;

import ai.wanaku.operator.wanaku.Wanaku;
import ai.wanaku.operator.wanaku.WanakuReconciler;
import ai.wanaku.operator.wanaku.WanakuSpec;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

public final class OperatorUtil {
    private static final Logger LOG = Logger.getLogger(OperatorUtil.class);
    public static final String ROUTER_BACKEND_DEPLOYMENT_FILE = "wanaku-router-deployment.yaml";
    public static final String ROUTER_BACKEND_INTERNAL_SERVICE_FILE = "wanaku-router-service-internal.yaml";
    public static final String ROUTER_BACKEND_EXTERNAL_SERVICE_FILE = "wanaku-router-service-external.yaml";
    public static final String WANAKU_CAPABILITY_DEPLOYMENT_FILE = "wanaku-capability-deployment.yaml";
    public static final String CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE =
            "camel-integration-capability-deployment.yaml";
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";
    public static final String ROUTER_VOLUME_CLAIM = "router-volume-claim";

    private OperatorUtil() {}

    private static void setupBackendContainer(Wanaku resource, DeploymentSpec spec, String host) {
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

        final WanakuSpec.RouterSpec routerSpec = resource.getSpec().getRouter();
        if (routerSpec != null) {
            // Set a custom image
            final String image = routerSpec.getImage();

            if (image != null) {
                service.setImage(image);
            }

            // Add custom environment variables from router spec if provided
            if (routerSpec.getEnv() != null && !routerSpec.getEnv().isEmpty()) {
                for (ai.wanaku.operator.wanaku.WanakuSpec.EnvVar env : routerSpec.getEnv()) {
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

    public static Deployment makeDesiredRouterBackendDeployment(Wanaku resource, Context<Wanaku> context, String host) {
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, WanakuReconciler.class, ROUTER_BACKEND_DEPLOYMENT_FILE);

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

    public static Service makeRouterInternalService(Wanaku resource) {
        Service service =
                ReconcilerUtils.loadYaml(Service.class, WanakuReconciler.class, ROUTER_BACKEND_INTERNAL_SERVICE_FILE);

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

    public static Route makeRouterExternalService(Wanaku resource) {
        Route route =
                ReconcilerUtils.loadYaml(Route.class, WanakuReconciler.class, ROUTER_BACKEND_EXTERNAL_SERVICE_FILE);

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

    public static PersistentVolumeClaim makeRouterVolumePVC(Wanaku resource) {
        PersistentVolumeClaim pvc =
                ReconcilerUtils.loadYaml(PersistentVolumeClaim.class, WanakuReconciler.class, SERVICES_VOLUME_PVC_FILE);

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

    public static PersistentVolumeClaim makeServicesVolumePVC(Wanaku resource, String serviceName) {
        PersistentVolumeClaim pvc =
                ReconcilerUtils.loadYaml(PersistentVolumeClaim.class, WanakuReconciler.class, SERVICES_VOLUME_PVC_FILE);

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
            DeploymentSpec spec, WanakuSpec.CapabilitiesSpec capabilitiesSpec, Supplier<List<EnvVar>> envVarSupplier) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.get(0);
        final String serviceName = capabilitiesSpec.getName();
        service.setName(serviceName);

        String serviceImage = capabilitiesSpec.getImage();
        service.setImage(serviceImage);

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
            Wanaku resource, WanakuSpec.CapabilitiesSpec capabilitiesSpec) {
        List<WanakuSpec.EnvVar> customEnv = capabilitiesSpec.getEnv();
        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();

        // Build the registration URI - use the internal service name
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
            Wanaku resource, WanakuSpec.CapabilitiesSpec capabilitiesSpec) {
        List<WanakuSpec.EnvVar> customEnv = capabilitiesSpec.getEnv();
        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();

        // Build the registration URI - use the internal service name
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

    private static void addCustomVars(List<WanakuSpec.EnvVar> customEnv, List<EnvVar> envVars) {
        // Add custom environment variables if provided
        if (customEnv != null && !customEnv.isEmpty()) {
            for (WanakuSpec.EnvVar env : customEnv) {
                EnvVar customEnvVar = new EnvVarBuilder()
                        .withName(env.getName())
                        .withValue(env.getValue())
                        .build();
                envVars.add(customEnvVar);
            }
        }
    }

    private static String getInternalRegistrationUri(Wanaku resource) {
        return "http://internal-" + resource.getMetadata().getName() + ":8080/";
    }

    public static Deployment makeDesiredWanakuCapabilityDeployment(
            Wanaku resource, Context<Wanaku> context, WanakuSpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, WanakuReconciler.class, WANAKU_CAPABILITY_DEPLOYMENT_FILE);

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

        setupCapabilityContainer(
                deploymentSpec, capabilitiesSpec, () -> computeWanakuCapabilitiesEnvVars(resource, capabilitiesSpec));

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    public static Deployment makeDesiredCiCCapabilityDeployment(
            Wanaku resource, Context<Wanaku> context, WanakuSpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment = ReconcilerUtils.loadYaml(
                Deployment.class, WanakuReconciler.class, CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE);

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

        setupCapabilityContainer(
                deploymentSpec,
                capabilitiesSpec,
                () -> computeCamelIntegrationCapabilitiesEnvVars(resource, capabilitiesSpec));

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    public static Service makeCapabilityInternalService(Wanaku resource, WanakuSpec.CapabilitiesSpec capabilitiesSpec) {
        Service service =
                ReconcilerUtils.loadYaml(Service.class, WanakuReconciler.class, CAPABILITY_INTERNAL_SERVICE_FILE);

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
