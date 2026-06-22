package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.security.ServiceAuthenticator;
import ai.wanaku.core.services.api.ServiceCatalogService;
import ai.wanaku.operator.util.CamelRoutePackager;
import ai.wanaku.operator.util.CapabilityResourceFactory;
import ai.wanaku.operator.util.EnvironmentVariables;
import ai.wanaku.operator.util.OperatorSecurityConfig;

import static ai.wanaku.operator.util.OperatorUtil.READY_CONDITION;
import static ai.wanaku.operator.util.OperatorUtil.findCondition;
import static ai.wanaku.operator.util.OperatorUtil.getRouterBaseUrl;
import static ai.wanaku.operator.util.OperatorUtil.readyCondition;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE), name = "wanaku-camel-route")
@CSVMetadata(
        displayName = "Wanaku Camel Route operator",
        description = "Deploys and manages Wanaku Camel Routes as service catalogs")
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
@RBACRule(
        apiGroups = "",
        resources = {"services"},
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
        apiGroups = "",
        resources = {"persistentvolumeclaims"},
        verbs = {
            RBACVerbs.GET,
            RBACVerbs.LIST,
            RBACVerbs.WATCH,
            RBACVerbs.CREATE,
            RBACVerbs.UPDATE,
            RBACVerbs.PATCH,
            RBACVerbs.DELETE
        })
public class WanakuCamelRouteReconciler implements Reconciler<WanakuCamelRoute>, Cleaner<WanakuCamelRoute> {
    private static final Logger LOG = Logger.getLogger(WanakuCamelRouteReconciler.class);

    private ServiceAuthenticator serviceAuthenticator;

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<WanakuCamelRoute> reconcile(WanakuCamelRoute resource, Context<WanakuCamelRoute> context) {
        String crName = resource.getMetadata().getName();
        LOG.infof("Starting CamelRoute reconciliation for %s", crName);

        final String namespace = resource.getMetadata().getNamespace();

        final String routerRef = resource.getSpec().getRouterRef();
        if (routerRef == null || routerRef.isBlank()) {
            return setErrorStatus(
                    resource,
                    "ValidationError",
                    "routerRef must be specified in the WanakuCamelRoute spec to indicate which WanakuRouter to deploy to.");
        }

        if (resource.getSpec().getRoute() == null
                || resource.getSpec().getRoute().isEmpty()) {
            return setErrorStatus(resource, "ValidationError", "route must be specified in the WanakuCamelRoute spec.");
        }

        WanakuCamelRouteSpec.McpSpec mcp = resource.getSpec().getMcp();
        if (mcp == null
                || ((mcp.getTools() == null || mcp.getTools().isEmpty())
                        && (mcp.getResources() == null || mcp.getResources().isEmpty()))) {
            return setErrorStatus(
                    resource,
                    "ValidationError",
                    "mcp must define at least one tool or resource in the WanakuCamelRoute spec.");
        }

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
                                    + "Ensure the WanakuRouter resource is created before the WanakuCamelRoute.",
                            routerRef, namespace));
        }

        final WanakuTypes.AuthSpec authSpec = router.getSpec().getAuth();
        final String routerBaseUrl = getRouterBaseUrl(routerRef);
        final String catalogName = crName;

        String base64Zip;
        try {
            base64Zip = CamelRoutePackager.packageCamelRoute(resource.getSpec(), catalogName);
        } catch (IOException e) {
            return setErrorStatus(
                    resource,
                    "DeploymentError",
                    "Failed to package CamelRoute '%s': %s".formatted(catalogName, e.getMessage()));
        }

        try {
            deployServiceCatalog(routerBaseUrl, authSpec, catalogName, base64Zip);
        } catch (WanakuException e) {
            return setErrorStatus(resource, "DeploymentError", e.getMessage());
        }
        LOG.infof("Successfully deployed CamelRoute '%s' as service catalog", catalogName);

        deployCicInstance(resource, crName, namespace, routerBaseUrl, authSpec);
        LOG.infof("CIC instance deployed for CamelRoute '%s'", crName);

        final WanakuCamelRouteStatus status = new WanakuCamelRouteStatus();
        status.setDeployedCatalogName(catalogName);
        status.setRegisteredTools(extractToolNames(resource.getSpec()));
        status.setRegisteredResources(extractResourceNames(resource.getSpec()));
        final Condition previousReadyCondition = findCondition(
                resource.getStatus() != null ? resource.getStatus().getConditions() : null, READY_CONDITION);
        status.setConditions(List.of(readyCondition(
                resource.getMetadata().getGeneration(),
                previousReadyCondition,
                "WanakuCamelRoute deployment is ready")));
        resource.setStatus(status);

        return UpdateControl.patchStatus(resource);
    }

    @Override
    public DeleteControl cleanup(WanakuCamelRoute resource, Context<WanakuCamelRoute> context) {
        LOG.infof("Cleaning up CamelRoute for %s", resource.getMetadata().getName());

        final String routerRef = resource.getSpec().getRouterRef();
        if (routerRef == null || routerRef.isBlank()) {
            return DeleteControl.defaultDelete();
        }

        String deployedCatalogName =
                resource.getStatus() != null ? resource.getStatus().getDeployedCatalogName() : null;
        if (deployedCatalogName == null || deployedCatalogName.isBlank()) {
            return DeleteControl.defaultDelete();
        }

        WanakuTypes.AuthSpec authSpec = null;
        WanakuRouter router = kubernetesClient
                .resources(WanakuRouter.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(routerRef)
                .get();
        if (router != null) {
            authSpec = router.getSpec().getAuth();
        }

        final String routerBaseUrl = getRouterBaseUrl(routerRef);
        try {
            removeServiceCatalog(routerBaseUrl, authSpec, deployedCatalogName);
            LOG.infof("Removed service catalog '%s' from router", deployedCatalogName);
        } catch (Exception e) {
            LOG.warnf("Failed to remove service catalog '%s' during cleanup: %s", deployedCatalogName, e.getMessage());
        }

        return DeleteControl.defaultDelete();
    }

    private UpdateControl<WanakuCamelRoute> setErrorStatus(WanakuCamelRoute resource, String reason, String message) {
        LOG.warnf("WanakuCamelRoute '%s' error (%s): %s", resource.getMetadata().getName(), reason, message);

        final WanakuCamelRouteStatus status = new WanakuCamelRouteStatus();
        Condition condition = new ConditionBuilder()
                .withType(READY_CONDITION)
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

    private void deployServiceCatalog(String routerBaseUrl, WanakuTypes.AuthSpec authSpec, String name, String data)
            throws WanakuException {
        DataStore dataStore = new DataStore();
        dataStore.setName(name);
        dataStore.setData(data);

        try {
            ServiceCatalogService service = createServiceCatalogClient(routerBaseUrl, authSpec);
            service.deploy(dataStore);
        } catch (WebApplicationException e) {
            throw new WanakuException(
                    String.format(
                            "Failed to deploy service catalog '%s': HTTP %d - %s",
                            name, e.getResponse().getStatus(), e.getResponse().readEntity(String.class)),
                    e);
        } catch (Exception e) {
            throw new WanakuException("Failed to deploy service catalog '%s'".formatted(name), e);
        }
    }

    private void removeServiceCatalog(String routerBaseUrl, WanakuTypes.AuthSpec authSpec, String name) {
        ServiceCatalogService service = createServiceCatalogClient(routerBaseUrl, authSpec);
        service.remove(name);
    }

    private ServiceCatalogService createServiceCatalogClient(String routerBaseUrl, WanakuTypes.AuthSpec authSpec) {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(routerBaseUrl));

        if (OperatorSecurityConfig.isAuthEnabled(authSpec)) {
            if (serviceAuthenticator == null) {
                serviceAuthenticator = new ServiceAuthenticator(new OperatorSecurityConfig(authSpec));
            }
            String token = serviceAuthenticator.currentValidAccessToken();
            builder.register(new BearerTokenFilter(token));
        }

        return builder.build(ServiceCatalogService.class);
    }

    private static class BearerTokenFilter implements ClientRequestFilter {
        private final String token;

        BearerTokenFilter(String token) {
            this.token = token;
        }

        @Override
        public void filter(ClientRequestContext requestContext) {
            requestContext.getHeaders().putSingle("Authorization", "Bearer " + token);
        }
    }

    private static final int CIC_GRPC_PORT = 9190;

    private void deployCicInstance(
            WanakuCamelRoute resource,
            String crName,
            String namespace,
            String routerBaseUrl,
            WanakuTypes.AuthSpec authSpec) {

        String cicName = crName + "-cic";
        LOG.infof("Creating CIC instance '%s' in namespace '%s'", cicName, namespace);

        PersistentVolumeClaim pvc = ReconcilerUtilsInternal.loadYaml(
                PersistentVolumeClaim.class,
                WanakuCamelRouteReconciler.class,
                CapabilityResourceFactory.SERVICES_VOLUME_PVC_FILE);
        pvc.getMetadata().setName(cicName + "-volume-claim");
        pvc.getMetadata().setNamespace(namespace);
        pvc.getMetadata().getLabels().put("app", crName);
        pvc.getMetadata().getLabels().put("component", cicName);
        pvc.addOwnerReference(resource);
        PersistentVolumeClaim existingPvc = kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(pvc.getMetadata().getName())
                .get();
        if (existingPvc == null) {
            LOG.infof("Creating PVC '%s'", pvc.getMetadata().getName());
            kubernetesClient
                    .persistentVolumeClaims()
                    .inNamespace(namespace)
                    .resource(pvc)
                    .create();
        }

        Deployment deployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class,
                WanakuCamelRouteReconciler.class,
                CapabilityResourceFactory.CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE);
        configureCicDeployment(deployment, resource, crName, cicName, namespace, routerBaseUrl, authSpec);
        LOG.infof(
                "Creating Deployment '%s' with image '%s'",
                cicName, resource.getSpec().getImage());
        kubernetesClient
                .apps()
                .deployments()
                .inNamespace(namespace)
                .resource(deployment)
                .createOr(Replaceable::update);

        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class,
                WanakuCamelRouteReconciler.class,
                CapabilityResourceFactory.CAPABILITY_INTERNAL_SERVICE_FILE);
        service.getMetadata().setName(cicName);
        service.getMetadata().setNamespace(namespace);
        service.getMetadata().getLabels().put("app", crName);
        service.getMetadata().getLabels().put("component", cicName);
        service.getSpec().setSelector(Map.of("app", crName, "component", cicName));
        for (ServicePort port : service.getSpec().getPorts()) {
            port.setPort(CIC_GRPC_PORT);
            port.setTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(CIC_GRPC_PORT));
            port.setName("9190-tcp");
        }
        service.addOwnerReference(resource);
        LOG.infof("Creating Service '%s' on port %d", cicName, CIC_GRPC_PORT);
        kubernetesClient.services().inNamespace(namespace).resource(service).createOr(Replaceable::update);
    }

    private void configureCicDeployment(
            Deployment deployment,
            WanakuCamelRoute resource,
            String crName,
            String cicName,
            String namespace,
            String routerBaseUrl,
            WanakuTypes.AuthSpec authSpec) {

        deployment.getMetadata().setName(cicName);
        deployment.getMetadata().setNamespace(namespace);
        deployment.getMetadata().getLabels().put("app", crName);
        deployment.getMetadata().getLabels().put("component", cicName);

        DeploymentSpec spec = deployment.getSpec();
        spec.getSelector().getMatchLabels().put("app", crName);
        spec.getSelector().getMatchLabels().put("component", cicName);
        spec.getTemplate().getMetadata().getLabels().put("app", crName);
        spec.getTemplate().getMetadata().getLabels().put("component", cicName);

        String volumeName = cicName + "-volume";
        spec.getTemplate()
                .getSpec()
                .getContainers()
                .getFirst()
                .getVolumeMounts()
                .getFirst()
                .setName(volumeName);
        spec.getTemplate().getSpec().getVolumes().getFirst().setName(volumeName);
        spec.getTemplate()
                .getSpec()
                .getVolumes()
                .getFirst()
                .getPersistentVolumeClaim()
                .setClaimName(cicName + "-volume-claim");

        Container container = spec.getTemplate().getSpec().getContainers().getFirst();
        container.setName(cicName);
        container.setImage(resource.getSpec().getImage());
        container.setImagePullPolicy("Always");
        container.setEnv(buildCicEnvVars(crName, routerBaseUrl, authSpec));

        deployment.addOwnerReference(resource);
    }

    private static List<EnvVar> buildCicEnvVars(String crName, String routerBaseUrl, WanakuTypes.AuthSpec authSpec) {
        List<EnvVar> envVars = new ArrayList<>();

        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_NAME)
                .withValue(crName)
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_URL)
                .withValue(routerBaseUrl + "/")
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_ANNOUNCE_ADDRESS)
                .withValue(crName + "-cic")
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_GRPC_PORT)
                .withValue(String.valueOf(CIC_GRPC_PORT))
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG)
                .withValue(crName)
                .build());
        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG_SYSTEM)
                .withValue(crName)
                .build());

        if (OperatorSecurityConfig.isAuthEnabled(authSpec)) {
            String realm = authSpec.getAuthRealm();
            if (realm == null || realm.isBlank()) {
                realm = EnvironmentVariables.DEFAULT_AUTH_REALM;
            }
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT)
                    .withValue(authSpec.getAuthServer() + "/realms/" + realm)
                    .build());
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET)
                    .withValue(OperatorSecurityConfig.resolveClientSecret())
                    .build());
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_ID)
                    .withValue("wanaku-service")
                    .build());
        }

        return envVars;
    }

    private static List<String> extractToolNames(WanakuCamelRouteSpec spec) {
        if (spec.getMcp() == null || spec.getMcp().getTools() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WanakuCamelRouteSpec.ToolSpec tool : spec.getMcp().getTools()) {
            names.add(tool.getName());
        }
        return names;
    }

    private static List<String> extractResourceNames(WanakuCamelRouteSpec spec) {
        if (spec.getMcp() == null || spec.getMcp().getResources() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WanakuCamelRouteSpec.ResourceSpec resource : spec.getMcp().getResources()) {
            names.add(resource.getName());
        }
        return names;
    }
}
