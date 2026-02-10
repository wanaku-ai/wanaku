package ai.wanaku.operator.wanaku;

import static ai.wanaku.operator.util.Matchers.match;
import static ai.wanaku.operator.util.OperatorUtil.createVolumeClaimName;
import static ai.wanaku.operator.util.OperatorUtil.makeCapabilityInternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredCiCCapabilityDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredRouterBackendDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredWanakuCapabilityDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterExternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterIngress;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterInternalService;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.OperatorUtil;
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
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

@ControllerConfiguration(informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE), name = "wanaku")
@CSVMetadata(displayName = "Wanaku operator", description = "A simple operator that can deploy and manage Wanaku")
public class WanakuReconciler implements Reconciler<Wanaku> {
    private static final Logger LOG = Logger.getLogger(WanakuReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Wanaku> reconcile(Wanaku resource, Context<Wanaku> context) throws Exception {
        LOG.infof("Starting reconciliation for %s", resource.getMetadata().getName());

        final String namespace = resource.getMetadata().getNamespace();

        final WanakuStatus wanakuStatus = new WanakuStatus();
        deployRouter(resource, context, namespace, wanakuStatus);
        deployCapabilities(resource, context, namespace);

        resource.setStatus(wanakuStatus);

        return UpdateControl.patchStatus(resource);
    }

    private void deployRouter(Wanaku resource, Context<Wanaku> context, String namespace, WanakuStatus wanakuStatus)
            throws WanakuException {
        // Create PVCs first, before creating the deployment
        createRouterPVCs(resource, namespace);

        // Create the internal service (cluster IP)
        final Service desiredExternalService = makeRouterInternalService(resource);
        Service existingExternalService;
        try {
            existingExternalService =
                    context.getSecondaryResource(Service.class).orElse(null);
        } catch (Exception e) {
            LOG.warnf("There is no existing service");
            existingExternalService = null;
        }
        if (!match(desiredExternalService, existingExternalService)) {
            String ns = resource.getMetadata().getNamespace();
            LOG.infof(
                    "Creating or updating Service %s in %s",
                    desiredExternalService.getMetadata().getName(), ns);

            kubernetesClient
                    .services()
                    .inNamespace(ns)
                    .resource(desiredExternalService)
                    .createOr(Replaceable::update);
        }

        // Create the external service - use OpenShift Route if available, otherwise Kubernetes Ingress
        String host;
        if (isOpenShiftCluster()) {
            LOG.info("OpenShift cluster detected, using Route for external access");
            final OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
            host = createRouteAndGetHost(resource, namespace, openShiftClient);
        } else {
            LOG.info("Kubernetes cluster detected, using Ingress for external access");
            host = createIngressAndGetHost(resource, namespace);
        }
        wanakuStatus.setHost("http://" + host);
        wanakuStatus.setSseEndpoint("http://" + host + "/mcp/sse");
        wanakuStatus.setStreamableEndpoint("http://" + host + "/mcp");

        // Create the router deployment
        final Deployment desiredDeployment = makeDesiredRouterBackendDeployment(resource, context, host);

        Deployment existingDeployment;
        try {
            existingDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
        } catch (Exception e) {
            LOG.warnf("There is no existing deployment");
            existingDeployment = null;
        }

        if (!match(desiredDeployment, existingDeployment)) {
            String ns = resource.getMetadata().getNamespace();
            LOG.infof(
                    "Creating or updating Deployment %s in %s",
                    desiredDeployment.getMetadata().getName(), ns);

            kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(ns)
                    .resource(desiredDeployment)
                    .createOr(Replaceable::update);
        }
    }

    private static String createRouteAndGetHost(Wanaku resource, String namespace, OpenShiftClient openShiftClient) {
        final Route desiredRoute = makeRouterExternalService(resource);
        Route existingRoute;
        try {
            existingRoute = openShiftClient
                    .routes()
                    .inNamespace(namespace)
                    .withName(desiredRoute.getMetadata().getName())
                    .get();
        } catch (Exception e) {
            LOG.warnf("There is no existing service");
            existingRoute = null;
        }
        if (!match(desiredRoute, existingRoute)) {
            String ns = resource.getMetadata().getNamespace();
            LOG.infof(
                    "Creating or updating Service %s in %s",
                    desiredRoute.getMetadata().getName(), ns);

            final Route created = openShiftClient
                    .routes()
                    .inNamespace(ns)
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

    private String createIngressAndGetHost(Wanaku resource, String namespace) {
        // Get host from spec - required for Kubernetes Ingress
        WanakuSpec.IngressSpec ingressSpec = resource.getSpec().getIngress();
        if (ingressSpec == null
                || ingressSpec.getHost() == null
                || ingressSpec.getHost().isBlank()) {
            throw new WanakuException(
                    "Ingress host must be specified in spec.ingress.host when deploying on Kubernetes. "
                            + "OpenShift clusters auto-generate the host via Routes.");
        }

        String host = ingressSpec.getHost();
        final Ingress desiredIngress = makeRouterIngress(resource, host);

        // Get existing ingress - returns null if not found
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
        return kubernetesClient.supports("route.openshift.io/v1", "Route");
    }

    private void deployCapabilities(Wanaku resource, Context<Wanaku> context, String namespace) {
        if (resource.getSpec().getCapabilities() == null
                || resource.getSpec().getCapabilities().isEmpty()) {
            LOG.infof("No capabilities to deploy for %s", resource.getMetadata().getName());
            return;
        }

        for (WanakuSpec.CapabilitiesSpec capabilitiesSpec : resource.getSpec().getCapabilities()) {
            String capabilityName = capabilitiesSpec.getName();
            LOG.infof("Deploying capability: %s", capabilityName);
            createCapabilityPVCs(resource, namespace, capabilityName);

            // Create the capability deployment

            Deployment desiredDeployment;

            if (capabilitiesSpec.getType() == null) {
                desiredDeployment = makeDesiredWanakuCapabilityDeployment(resource, context, capabilitiesSpec);
            } else {
                if ("camel-integration-capability".equals(capabilitiesSpec.getType())) {
                    desiredDeployment = makeDesiredCiCCapabilityDeployment(resource, context, capabilitiesSpec);
                } else {
                    LOG.error("Invalid capability type: " + capabilitiesSpec.getType());
                    throw new WanakuException("Invalid capability type: " + capabilitiesSpec.getType());
                }
            }

            Deployment existingDeployment = kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(capabilityName)
                    .get();

            if (!match(desiredDeployment, existingDeployment)) {
                LOG.infof("Creating or updating Deployment %s in %s", capabilityName, namespace);
                kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .resource(desiredDeployment)
                        .createOr(Replaceable::update);
            }

            // Create the internal service for the capability
            final Service desiredService = makeCapabilityInternalService(resource, capabilitiesSpec);

            Service existingService = kubernetesClient
                    .services()
                    .inNamespace(namespace)
                    .withName(capabilityName)
                    .get();

            if (!match(desiredService, existingService)) {
                LOG.infof("Creating or updating Service %s in %s", capabilityName, namespace);
                kubernetesClient
                        .services()
                        .inNamespace(namespace)
                        .resource(desiredService)
                        .createOr(Replaceable::update);
            }
        }
    }

    private void createRouterPVCs(Wanaku resource, String namespace) {
        // Create services-volume PVC
        final PersistentVolumeClaim servicesVolumePVC = OperatorUtil.makeRouterVolumePVC(resource);
        PersistentVolumeClaim existingServicesVolume = kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(OperatorUtil.ROUTER_VOLUME_CLAIM)
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

    private void createCapabilityPVCs(Wanaku resource, String namespace, String serviceName) {
        // Create services-volume PVC
        final PersistentVolumeClaim servicesVolumePVC = OperatorUtil.makeServicesVolumePVC(resource, serviceName);
        PersistentVolumeClaim existingServicesVolume = kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(createVolumeClaimName(serviceName))
                .get();

        if (!match(servicesVolumePVC, existingServicesVolume)) {
            LOG.infof("Creating or updating PVC %s in %s", createVolumeClaimName(serviceName), namespace);
            kubernetesClient
                    .persistentVolumeClaims()
                    .inNamespace(namespace)
                    .resource(servicesVolumePVC)
                    .createOr(Replaceable::update);
        }
    }
}
