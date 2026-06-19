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
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.services.api.ServiceCatalogService;
import ai.wanaku.operator.util.CamelRoutePackager;
import ai.wanaku.operator.util.OperatorAuthHelper;

import static ai.wanaku.operator.util.OperatorUtil.READY_CONDITION;
import static ai.wanaku.operator.util.OperatorUtil.findCondition;
import static ai.wanaku.operator.util.OperatorUtil.getRouterBaseUrl;
import static ai.wanaku.operator.util.OperatorUtil.readyCondition;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE), name = "wanaku-camel-route")
@CSVMetadata(
        displayName = "Wanaku Camel Route operator",
        description = "Deploys and manages Wanaku Camel Routes as service catalogs")
public class WanakuCamelRouteReconciler implements Reconciler<WanakuCamelRoute>, Cleaner<WanakuCamelRoute> {
    private static final Logger LOG = Logger.getLogger(WanakuCamelRouteReconciler.class);

    private final OperatorAuthHelper authHelper = new OperatorAuthHelper();

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

    private void removeServiceCatalog(String routerBaseUrl, WanakuTypes.AuthSpec authSpec, String name)
            throws IOException {
        ServiceCatalogService service = createServiceCatalogClient(routerBaseUrl, authSpec);
        service.remove(name);
    }

    private ServiceCatalogService createServiceCatalogClient(String routerBaseUrl, WanakuTypes.AuthSpec authSpec)
            throws IOException {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(routerBaseUrl));

        if (OperatorAuthHelper.isAuthEnabled(authSpec)) {
            String token = authHelper.getToken(authSpec);
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
