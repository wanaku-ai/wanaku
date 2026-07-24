package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteIngress;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import ai.wanaku.core.util.StringHelper;
import ai.wanaku.operator.util.OperatorUtil;
import ai.wanaku.operator.util.RouterResourceFactory;

import static ai.wanaku.operator.util.Matchers.match;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), name = "wanaku-router")
@CSVMetadata(displayName = "Wanaku Router operator", description = "Deploys and manages the Wanaku Router")
@RBACRule(
        apiGroups = "",
        resources = {"persistentvolumeclaims", "services", "configmaps", "secrets", "serviceaccounts"},
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
        apiGroups = "route.openshift.io",
        resources = {"routes"},
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
        apiGroups = "networking.k8s.io",
        resources = {"ingresses"},
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
        apiGroups = "rbac.authorization.k8s.io",
        resources = {"roles", "rolebindings"},
        verbs = {
            RBACVerbs.GET,
            RBACVerbs.LIST,
            RBACVerbs.WATCH,
            RBACVerbs.CREATE,
            RBACVerbs.UPDATE,
            RBACVerbs.PATCH,
            RBACVerbs.DELETE
        })
public class WanakuRouterReconciler implements Reconciler<WanakuRouter> {
    private static final Logger LOG = Logger.getLogger(WanakuRouterReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<WanakuRouter> reconcile(WanakuRouter resource, Context<WanakuRouter> context) {
        LOG.infof(
                "Starting router reconciliation for %s", resource.getMetadata().getName());

        ValidateSpecResult validation = validateSpec(resource);
        if (!validation.valid) {
            return setErrorStatus(resource, "ValidationError", validation.errorMessage);
        }

        WanakuTypes.ExposureSpec exposureSpec = resource.getSpec().getExposure();
        if (exposureSpec != null && exposureSpec.getType() == WanakuTypes.ExposureType.ROUTE && !isOpenShiftCluster()) {
            LOG.errorf(
                    "WanakuRouter '%s' requests type=Route but the cluster does not have the"
                            + " OpenShift Route API (route.openshift.io)",
                    resource.getMetadata().getName());
            return setErrorStatus(
                    resource, "ValidationError", "spec.exposure.type is Route but this is not an OpenShift cluster");
        }

        final String namespace = resource.getMetadata().getNamespace();
        final WanakuRouterStatus wanakuStatus = new WanakuRouterStatus();
        deployRouter(resource, context, namespace, wanakuStatus);

        final Condition previousReadyCondition = OperatorUtil.findCondition(
                resource.getStatus() != null ? resource.getStatus().getConditions() : null,
                OperatorUtil.READY_CONDITION);
        wanakuStatus.setConditions(List.of(OperatorUtil.readyCondition(
                resource.getMetadata().getGeneration(), previousReadyCondition, "WanakuRouter deployment is ready")));

        resource.setStatus(wanakuStatus);
        return UpdateControl.patchStatus(resource);
    }

    private void deployRouter(
            WanakuRouter resource, Context<WanakuRouter> context, String namespace, WanakuRouterStatus wanakuStatus) {

        createRouterPVCs(resource, namespace);

        final Service desiredInternalService = RouterResourceFactory.makeRouterInternalService(resource);
        Service existingInternalService = kubernetesClient
                .services()
                .inNamespace(namespace)
                .withName(desiredInternalService.getMetadata().getName())
                .get();
        if (!match(desiredInternalService, existingInternalService)) {
            LOG.infof(
                    "Creating or updating Service %s in %s",
                    desiredInternalService.getMetadata().getName(), namespace);
            kubernetesClient
                    .services()
                    .inNamespace(namespace)
                    .resource(desiredInternalService)
                    .createOr(Replaceable::update);
        }

        String host = reconcileExternalAccess(resource, namespace);

        wanakuStatus.setHost("https://" + host);
        wanakuStatus.setSseEndpoint("https://%s/mcp/sse".formatted(host));
        wanakuStatus.setStreamableEndpoint("https://%s/mcp/".formatted(host));

        final Deployment desiredDeployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(resource, context, host);
        Deployment existingDeployment = kubernetesClient
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(desiredDeployment.getMetadata().getName())
                .get();
        if (!match(desiredDeployment, existingDeployment)) {
            LOG.infof(
                    "Creating or updating Deployment %s in %s",
                    desiredDeployment.getMetadata().getName(), namespace);
            kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .resource(desiredDeployment)
                    .createOr(Replaceable::update);
        }
    }

    private String reconcileExternalAccess(WanakuRouter resource, String namespace) {
        WanakuTypes.ExposureSpec exposureSpec = resource.getSpec().getExposure();
        if (exposureSpec == null
                || exposureSpec.getType() == null
                || exposureSpec.getType() == WanakuTypes.ExposureType.NONE) {
            return "internal-" + resource.getMetadata().getName();
        }
        if (exposureSpec.getType() == WanakuTypes.ExposureType.ROUTE) {
            OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
            return createRouteAndGetHost(resource, namespace, openShiftClient);
        }
        return createIngressAndGetHost(resource, namespace);
    }

    private static String createRouteAndGetHost(
            WanakuRouter resource, String namespace, OpenShiftClient openShiftClient) {
        final Route desiredRoute = RouterResourceFactory.makeRouterExternalService(resource);
        Route existingRoute;
        try {
            existingRoute = openShiftClient
                    .routes()
                    .inNamespace(namespace)
                    .withName(desiredRoute.getMetadata().getName())
                    .get();
        } catch (Exception e) {
            LOG.warnf(e, "There is no existing service");
            existingRoute = null;
        }
        if (!match(desiredRoute, existingRoute)) {
            LOG.infof(
                    "Creating or updating Route %s in %s",
                    desiredRoute.getMetadata().getName(), namespace);
            final Route created = openShiftClient
                    .routes()
                    .inNamespace(namespace)
                    .resource(desiredRoute)
                    .createOr(Replaceable::update);
            final List<RouteIngress> routeIngresses = created.getStatus().getIngress();
            if (routeIngresses != null && !routeIngresses.isEmpty()) {
                final RouteIngress ingress = routeIngresses.getFirst();
                if (ingress != null) {
                    return ingress.getHost();
                }
            }
            final Route refreshedRoute = openShiftClient
                    .routes()
                    .inNamespace(namespace)
                    .withName(desiredRoute.getMetadata().getName())
                    .get();
            return refreshedRoute.getStatus().getIngress().getFirst().getHost();
        } else {
            return existingRoute.getStatus().getIngress().getFirst().getHost();
        }
    }

    private String createIngressAndGetHost(WanakuRouter resource, String namespace) {
        String host = resource.getSpec().getExposure().getHost();
        final Ingress desiredIngress = RouterResourceFactory.makeRouterIngress(resource, host);
        Ingress existingIngress = kubernetesClient
                .network()
                .v1()
                .ingresses()
                .inNamespace(namespace)
                .withName(desiredIngress.getMetadata().getName())
                .get();
        if (!match(desiredIngress, existingIngress)) {
            LOG.infof(
                    "Creating or updating Ingress %s in %s",
                    desiredIngress.getMetadata().getName(), namespace);
            kubernetesClient
                    .network()
                    .v1()
                    .ingresses()
                    .inNamespace(namespace)
                    .resource(desiredIngress)
                    .createOr(Replaceable::update);
        }
        return host;
    }

    private boolean isOpenShiftCluster() {
        try {
            APIGroup apiGroup = kubernetesClient.getApiGroup("route.openshift.io");
            return apiGroup != null;
        } catch (RuntimeException e) {
            LOG.warn("Failed to detect OpenShift cluster.", e);
            return false;
        }
    }

    private void createRouterPVCs(WanakuRouter resource, String namespace) {
        final PersistentVolumeClaim servicesVolumePVC = RouterResourceFactory.makeRouterVolumePVC(resource);
        PersistentVolumeClaim existingServicesVolume = kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(RouterResourceFactory.ROUTER_VOLUME_CLAIM)
                .get();
        if (!match(servicesVolumePVC, existingServicesVolume)) {
            LOG.infof("Creating or updating PVC route-volume-claim in %s", namespace);
            kubernetesClient
                    .persistentVolumeClaims()
                    .inNamespace(namespace)
                    .resource(servicesVolumePVC)
                    .createOr(Replaceable::update);
        }
    }

    private UpdateControl<WanakuRouter> setErrorStatus(WanakuRouter resource, String reason, String message) {
        LOG.warnf("WanakuRouter '%s' error (%s): %s", resource.getMetadata().getName(), reason, message);
        WanakuRouterStatus status = new WanakuRouterStatus();
        Condition condition = new ConditionBuilder()
                .withType(OperatorUtil.READY_CONDITION)
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

    static class ValidateSpecResult {
        final boolean valid;
        final String errorMessage;

        ValidateSpecResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        static final ValidateSpecResult OK = new ValidateSpecResult(true, null);

        static ValidateSpecResult invalid(String message) {
            return new ValidateSpecResult(false, message);
        }
    }

    ValidateSpecResult validateSpec(WanakuRouter resource) {
        WanakuTypes.ExposureSpec exposureSpec =
                resource.getSpec() != null ? resource.getSpec().getExposure() : null;

        if (exposureSpec == null
                || exposureSpec.getType() == null
                || exposureSpec.getType() == WanakuTypes.ExposureType.NONE) {
            return ValidateSpecResult.OK;
        }

        if (exposureSpec.getType() == WanakuTypes.ExposureType.INGRESS
                && StringHelper.isBlank(exposureSpec.getHost())) {
            return ValidateSpecResult.invalid("spec.exposure.host is required when spec.exposure.type is Ingress");
        }

        return ValidateSpecResult.OK;
    }
}
