package ai.wanaku.operator.wanaku;

import static ai.wanaku.operator.util.Matchers.match;
import static ai.wanaku.operator.util.OperatorUtil.createVolumeClaimName;
import static ai.wanaku.operator.util.OperatorUtil.makeCapabilityInternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredCapabilityDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredRouterBackendDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterExternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterInternalService;

import ai.wanaku.operator.util.OperatorUtil;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteIngress;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;

public class WanakuReconciler implements Reconciler<Wanaku> {
    private static final Logger LOG = Logger.getLogger(WanakuReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Wanaku> reconcile(Wanaku resource, Context<Wanaku> context) throws Exception {
        LOG.infof("Starting reconciliation for %s", resource.getMetadata().getName());

        final String namespace = resource.getMetadata().getNamespace();

        deployRouter(resource, context, namespace);
        deployCapabilities(resource, context, namespace);

        return UpdateControl.noUpdate();
    }

    private void deployRouter(Wanaku resource, Context<Wanaku> context, String namespace) {
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

        // Create the external service (cluster IP)
        final OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);

        String host = createRouteAndGetHost(resource, namespace, openShiftClient);

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
            existingRoute = openShiftClient.routes().inNamespace(namespace).withName(desiredRoute.getMetadata().getName()).get();
        } catch (Exception e) {
            LOG.warnf("There is no existing service");
            existingRoute = null;
        }
        if (!match(desiredRoute, existingRoute)) {
            String ns = resource.getMetadata().getNamespace();
            LOG.infof(
                    "Creating or updating Service %s in %s",
                    desiredRoute.getMetadata().getName(), ns);

            final Route created = openShiftClient.routes().inNamespace(ns).resource(desiredRoute).createOr(Replaceable::update);
            final List<RouteIngress> routeIngresses = created.getStatus().getIngress();
            if (routeIngresses != null && !routeIngresses.isEmpty()) {
                final RouteIngress ingress = routeIngresses.getFirst();
                if (ingress != null) {
                    return ingress.getHost();
                }
            }

            final Route refreshedRoute =
                    openShiftClient.routes().inNamespace(namespace).withName(desiredRoute.getMetadata().getName()).get();

            return refreshedRoute.getStatus().getIngress().getFirst().getHost();
        } else {
            return existingRoute.getStatus().getIngress().getFirst().getHost();
        }
    }

    private void deployCapabilities(Wanaku resource, Context<Wanaku> context, String namespace) {
        if (resource.getSpec().getServices() == null
                || resource.getSpec().getServices().isEmpty()) {
            LOG.infof("No capabilities to deploy for %s", resource.getMetadata().getName());
            return;
        }

        for (WanakuSpec.ServiceSpec serviceSpec : resource.getSpec().getServices()) {
            String serviceName = serviceSpec.getName();
            LOG.infof("Deploying capability: %s", serviceName);
            createCapabilityPVCs(resource, namespace, serviceName);

            // Create the capability deployment
            final Deployment desiredDeployment = makeDesiredCapabilityDeployment(resource, context, serviceSpec);

            String deploymentName = serviceName;
            Deployment existingDeployment = kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();

            if (!match(desiredDeployment, existingDeployment)) {
                LOG.infof("Creating or updating Deployment %s in %s", deploymentName, namespace);
                kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .resource(desiredDeployment)
                        .createOr(Replaceable::update);
            }

            // Create the internal service for the capability
            final Service desiredService = makeCapabilityInternalService(resource, serviceSpec);

            Service existingService = kubernetesClient
                    .services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();

            if (!match(desiredService, existingService)) {
                LOG.infof("Creating or updating Service %s in %s", serviceName, namespace);
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
