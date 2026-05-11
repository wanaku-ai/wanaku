package ai.wanaku.operator.util;

import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
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
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterReconciler;
import ai.wanaku.operator.wanaku.WanakuRouterSpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Factory for creating Kubernetes resources related to WanakuRouter deployments.
 *
 * <p>Handles the creation of router deployments, internal/external services,
 * ingresses, routes, and persistent volume claims.</p>
 */
public final class RouterResourceFactory {
    private static final Logger LOG = Logger.getLogger(RouterResourceFactory.class);

    public static final String ROUTER_BACKEND_DEPLOYMENT_FILE = "wanaku-router-deployment.yaml";
    public static final String ROUTER_BACKEND_INTERNAL_SERVICE_FILE = "wanaku-router-service-internal.yaml";
    public static final String ROUTER_BACKEND_EXTERNAL_SERVICE_FILE = "wanaku-router-service-external.yaml";
    public static final String ROUTER_INGRESS_FILE = "wanaku-router-ingress.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";
    public static final String ROUTER_VOLUME_CLAIM = "router-volume-claim";

    private RouterResourceFactory() {}

    /**
     * Creates the desired router backend deployment for the given WanakuRouter resource.
     *
     * @param resource the WanakuRouter custom resource
     * @param context the reconciler context
     * @param host the external host for the router
     * @return a fully configured Deployment
     */
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

    /**
     * Creates the internal (ClusterIP) service for the router.
     *
     * @param resource the WanakuRouter custom resource
     * @return a fully configured Service
     */
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

    /**
     * Creates an OpenShift Route for external access to the router.
     *
     * @param resource the WanakuRouter custom resource
     * @return a fully configured Route
     */
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

    /**
     * Creates a Kubernetes Ingress for external access to the router.
     *
     * @param resource the WanakuRouter custom resource
     * @param host the host for the ingress rule
     * @return a fully configured Ingress
     */
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

    /**
     * Creates a PersistentVolumeClaim for router storage.
     *
     * @param resource the WanakuRouter custom resource
     * @return a fully configured PersistentVolumeClaim
     */
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

        String realm = OperatorUtil.resolveAuthRealm(resource);

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
        String resolvedPolicy = OperatorUtil.resolveImagePullPolicy(componentPolicy, globalPolicy);
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

    private static String routerName(String deploymentName) {
        return deploymentName + "-mcp-router";
    }
}
