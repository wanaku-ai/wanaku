package ai.wanaku.operator.util;

import ai.wanaku.operator.wanaku.Wanaku;
import ai.wanaku.operator.wanaku.WanakuReconciler;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
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

    private OperatorUtil() {}

    private static void setupBackendContainer(Wanaku resource, DeploymentSpec spec) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.stream()
                .filter(c -> c.getName().equals("wanaku-mcp-router"))
                .findFirst()
                .get();

        final String authServer = resource.getSpec().getAuth().getAuthServer();
        final String authProxy = resource.getSpec().getAuth().getAuthProxy();

        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_SERVER)
                .withValue(authServer)
                .build();
        EnvVar authProxyEnv = new EnvVarBuilder()
                .withName(EnvironmentVariables.AUTH_PROXY)
                .withValue(authProxy)
                .build();

        service.setEnv(List.of(authServerEnv, authProxyEnv));
    }

    public static Deployment makeDesiredRouterBackendDeployment(Wanaku resource, Context<Wanaku> context) {
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, WanakuReconciler.class, ROUTER_BACKEND_DEPLOYMENT_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        desiredDeployment.getMetadata().setName(deploymentName);
        desiredDeployment.getMetadata().setNamespace(ns);

        final DeploymentSpec serviceSpec = desiredDeployment.getSpec();

        serviceSpec.getSelector().getMatchLabels().put("app", deploymentName);
        serviceSpec.getSelector().getMatchLabels().put("component", "wanaku-router-backend");
        serviceSpec.getTemplate().getMetadata().getLabels().put("app", deploymentName);
        serviceSpec.getTemplate().getMetadata().getLabels().put("component", "wanaku-router-backend");

        setupBackendContainer(resource, serviceSpec);
        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    public static Service makeRouterInternalService(Wanaku resource) {
        Service service =
                ReconcilerUtils.loadYaml(Service.class, WanakuReconciler.class, ROUTER_BACKEND_INTERNAL_SERVICE_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating new external service for deployment: %s", deploymentName);
        service.getMetadata().setName("internal-" + deploymentName);
        service.getMetadata().setNamespace(ns);
        service.getMetadata().getLabels().put("app", deploymentName);
        service.getMetadata().getLabels().put("component", "wanaku-router-backend");
        service.getSpec().getSelector().put("app", deploymentName);

        ServiceSpec serviceSpec = service.getSpec();
        serviceSpec.setSelector(Map.of("app", deploymentName, "component", "wanaku-router-backend"));

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
        route.getMetadata().getLabels().put("app", deploymentName);
        route.getMetadata().getLabels().put("component", "wanaku-router-backend");
        route.getSpec().getTo().setName("internal-" + deploymentName);

        route.addOwnerReference(resource);

        return route;
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
