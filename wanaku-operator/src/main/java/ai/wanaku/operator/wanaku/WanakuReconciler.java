package ai.wanaku.operator.wanaku;

import static ai.wanaku.operator.util.Matchers.match;
import static ai.wanaku.operator.util.OperatorUtil.makeCapabilityInternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredCapabilityDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterExternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeRouterInternalService;

import ai.wanaku.operator.util.OperatorUtil;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.inject.Inject;
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
        // Create the router deployment
        final Deployment desiredDeployment = OperatorUtil.makeDesiredRouterBackendDeployment(resource, context);

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

        final Route desiredRoute = makeRouterExternalService(resource);
        Route existingRoute;
        try {
            existingRoute = context.getSecondaryResource(Route.class).orElse(null);
        } catch (Exception e) {
            LOG.warnf("There is no existing service");
            existingRoute = null;
        }
        if (!match(desiredRoute, existingRoute)) {
            String ns = resource.getMetadata().getNamespace();
            LOG.infof(
                    "Creating or updating Service %s in %s",
                    desiredRoute.getMetadata().getName(), ns);

            openShiftClient.routes().inNamespace(ns).resource(desiredRoute).createOr(Replaceable::update);
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
}
