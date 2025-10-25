package ai.wanaku.operator.util;

import ai.wanaku.operator.wanaku.Wanaku;
import ai.wanaku.operator.wanaku.WanakuReconciler;
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
import org.jboss.logging.Logger;

public final class OperatorUtil {
    private static final Logger LOG = Logger.getLogger(OperatorUtil.class);
    public static final String ROUTER_BACKEND_DEPLOYMENT_FILE = "wanaku-router-deployment.yaml";
    public static final String ROUTER_BACKEND_INTERNAL_SERVICE_FILE = "wanaku-router-service-internal.yaml";
    public static final String ROUTER_BACKEND_EXTERNAL_SERVICE_FILE = "wanaku-router-service-external.yaml";
    public static final String CAPABILITY_DEPLOYMENT_FILE = "wanaku-capability-deployment.yaml";
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";
    public static final String SHARED_DATA_PVC_FILE = "shared-data-pvc.yaml";
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
            authProxy = host;
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

        // Add custom environment variables from router spec if provided
        if (resource.getSpec().getRouter() != null
                && resource.getSpec().getRouter().getEnv() != null
                && !resource.getSpec().getRouter().getEnv().isEmpty()) {
            for (ai.wanaku.operator.wanaku.WanakuSpec.EnvVar env :
                    resource.getSpec().getRouter().getEnv()) {
                EnvVar customEnvVar = new EnvVarBuilder()
                        .withName(env.getName())
                        .withValue(env.getValue())
                        .build();
                envVars.add(customEnvVar);
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

        // Override port if specified in router spec
        if (resource.getSpec().getRouter() != null
                && resource.getSpec().getRouter().getPort() != null) {
            Integer customPort = resource.getSpec().getRouter().getPort();
            LOG.infof("Using custom router port: %d", customPort);

            // Update container port
            routerContainer.getPorts().get(0).setContainerPort(customPort);

            // Update liveness probe port
            if (routerContainer.getLivenessProbe() != null
                    && routerContainer.getLivenessProbe().getHttpGet() != null) {
                routerContainer
                        .getLivenessProbe()
                        .getHttpGet()
                        .setPort(new io.fabric8.kubernetes.api.model.IntOrString(customPort));
            }

            // Update readiness probe port
            if (routerContainer.getReadinessProbe() != null
                    && routerContainer.getReadinessProbe().getHttpGet() != null) {
                routerContainer
                        .getReadinessProbe()
                        .getHttpGet()
                        .setPort(new io.fabric8.kubernetes.api.model.IntOrString(customPort));
            }
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

        // Override port if specified in router spec
        if (resource.getSpec().getRouter() != null
                && resource.getSpec().getRouter().getPort() != null) {
            Integer customPort = resource.getSpec().getRouter().getPort();
            LOG.infof("Using custom router service port: %d", customPort);

            // Update service port and targetPort
            serviceSpec.getPorts().get(0).setPort(customPort);
            serviceSpec.getPorts().get(0).setTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(customPort));
            serviceSpec.getPorts().get(0).setName(customPort + "-tcp");
        }

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

        // Override port name in route if specified in router spec
        if (resource.getSpec().getRouter() != null
                && resource.getSpec().getRouter().getPort() != null) {
            Integer customPort = resource.getSpec().getRouter().getPort();
            route.getSpec()
                    .getPort()
                    .setTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(customPort + "-tcp"));
            LOG.infof("Using custom router route port: %d-tcp", customPort);
        }

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
            Wanaku resource,
            DeploymentSpec spec,
            String serviceName,
            String serviceImage,
            List<ai.wanaku.operator.wanaku.WanakuSpec.EnvVar> customEnv) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.get(0);
        service.setName(serviceName);
        service.setImage(serviceImage);

        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();

        // Build the registration URI - use the internal service name
        String registrationUri = "http://internal-" + resource.getMetadata().getName() + ":8080/";

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
        if (customEnv != null && !customEnv.isEmpty()) {
            for (ai.wanaku.operator.wanaku.WanakuSpec.EnvVar env : customEnv) {
                EnvVar customEnvVar = new EnvVarBuilder()
                        .withName(env.getName())
                        .withValue(env.getValue())
                        .build();
                envVars.add(customEnvVar);
            }
        }

        service.setEnv(envVars);
    }

    public static Deployment makeDesiredCapabilityDeployment(
            Wanaku resource, Context<Wanaku> context, ai.wanaku.operator.wanaku.WanakuSpec.ServiceSpec serviceSpec) {
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, WanakuReconciler.class, CAPABILITY_DEPLOYMENT_FILE);

        String serviceName = serviceSpec.getName();
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
        deploymentSpec.getTemplate()
                .getSpec()
                .getVolumes()
                .getFirst()
                .setName(serviceName + "-volume");
        deploymentSpec
                .getTemplate()
                .getSpec()
                .getVolumes()
                .getFirst()
                .getPersistentVolumeClaim()
                .setClaimName(createVolumeClaimName(serviceName));

        setupCapabilityContainer(resource, deploymentSpec, serviceName, serviceSpec.getImage(), serviceSpec.getEnv());

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    public static Service makeCapabilityInternalService(
            Wanaku resource, ai.wanaku.operator.wanaku.WanakuSpec.ServiceSpec serviceSpec) {
        Service service =
                ReconcilerUtils.loadYaml(Service.class, WanakuReconciler.class, CAPABILITY_INTERNAL_SERVICE_FILE);

        String serviceName = serviceSpec.getName();
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
