package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.OperatorUtil;

import static ai.wanaku.operator.util.Matchers.match;
import static ai.wanaku.operator.util.OperatorUtil.createVolumeClaimName;
import static ai.wanaku.operator.util.OperatorUtil.makeCapabilityInternalService;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredCiCCapabilityDeployment;
import static ai.wanaku.operator.util.OperatorUtil.makeDesiredWanakuCapabilityDeployment;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE), name = "wanaku-capability")
@CSVMetadata(displayName = "Wanaku Capability operator", description = "Deploys and manages Wanaku Capabilities")
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
public class WanakuCapabilityReconciler implements Reconciler<WanakuCapability> {
    private static final Logger LOG = Logger.getLogger(WanakuCapabilityReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<WanakuCapability> reconcile(WanakuCapability resource, Context<WanakuCapability> context)
            throws Exception {
        LOG.infof(
                "Starting capability reconciliation for %s",
                resource.getMetadata().getName());

        final String namespace = resource.getMetadata().getNamespace();

        if (resource.getSpec().getRouterRef() == null
                || resource.getSpec().getRouterRef().isBlank()) {
            throw new WanakuException(
                    "routerRef must be specified in the WanakuCapability spec to indicate which WanakuRouter to register with.");
        }

        deployCapabilities(resource, context, namespace);

        return UpdateControl.noUpdate();
    }

    private void deployCapabilities(WanakuCapability resource, Context<WanakuCapability> context, String namespace) {
        if (resource.getSpec().getCapabilities() == null
                || resource.getSpec().getCapabilities().isEmpty()) {
            LOG.infof("No capabilities to deploy for %s", resource.getMetadata().getName());
            return;
        }

        for (WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec :
                resource.getSpec().getCapabilities()) {
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

    private void createCapabilityPVCs(WanakuCapability resource, String namespace, String serviceName) {
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
