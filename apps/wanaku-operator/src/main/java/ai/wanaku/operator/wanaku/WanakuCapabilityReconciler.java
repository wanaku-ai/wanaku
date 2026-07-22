package ai.wanaku.operator.wanaku;

import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.CapabilityResourceFactory;
import ai.wanaku.operator.util.OperatorConstants;
import ai.wanaku.operator.util.OperatorUtil;

import static ai.wanaku.operator.util.Matchers.match;

@ControllerConfiguration(
        informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE),
        name = "wanaku-capability")
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
    public UpdateControl<WanakuCapability> reconcile(WanakuCapability resource, Context<WanakuCapability> context) {
        LOG.infof(
                "Starting capability reconciliation for %s",
                resource.getMetadata().getName());

        try {
            final String namespace = resource.getMetadata().getNamespace();

            final String routerRef = resource.getSpec().getRouterRef();
            if (routerRef == null || routerRef.isBlank()) {
                return setErrorStatus(
                        resource,
                        "ValidationError",
                        "routerRef must be specified in the WanakuCapability spec to indicate which WanakuRouter to register with.");
            }

            // Verify the referenced WanakuRouter exists
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
                                        + "Ensure the WanakuRouter resource is created before the WanakuCapability.",
                                routerRef, namespace));
            }

            deployCapabilities(resource, context, namespace);
        } catch (RuntimeException e) {
            return setErrorStatus(resource, "ReconciliationError", e.getMessage());
        }

        final WanakuCapabilityStatus status = new WanakuCapabilityStatus();
        final Condition previousReadyCondition = OperatorUtil.findCondition(
                resource.getStatus() != null ? resource.getStatus().getConditions() : null,
                OperatorUtil.READY_CONDITION);
        status.setConditions(List.of(OperatorUtil.readyCondition(
                resource.getMetadata().getGeneration(),
                previousReadyCondition,
                "WanakuCapability deployment is ready")));
        resource.setStatus(status);

        return UpdateControl.patchStatus(resource);
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
                desiredDeployment = CapabilityResourceFactory.makeDesiredWanakuCapabilityDeployment(
                        resource, context, capabilitiesSpec);
            } else {
                if (OperatorConstants.CAMEL_INTEGRATION_CAPABILITY_TYPE.equals(capabilitiesSpec.getType())) {
                    desiredDeployment = CapabilityResourceFactory.makeDesiredCiCCapabilityDeployment(
                            resource, context, capabilitiesSpec);
                } else {
                    LOG.errorf("Invalid capability type: %s", capabilitiesSpec.getType());
                    throw new WanakuException("Invalid capability type: %s".formatted(capabilitiesSpec.getType()));
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
            final Service desiredService =
                    CapabilityResourceFactory.makeCapabilityInternalService(resource, capabilitiesSpec);

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
        final PersistentVolumeClaim servicesVolumePVC =
                CapabilityResourceFactory.makeServicesVolumePVC(resource, serviceName);
        PersistentVolumeClaim existingServicesVolume = kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(CapabilityResourceFactory.createVolumeClaimName(serviceName))
                .get();

        if (!match(servicesVolumePVC, existingServicesVolume)) {
            LOG.infof(
                    "Creating or updating PVC %s in %s",
                    CapabilityResourceFactory.createVolumeClaimName(serviceName), namespace);
            kubernetesClient
                    .persistentVolumeClaims()
                    .inNamespace(namespace)
                    .resource(servicesVolumePVC)
                    .createOr(Replaceable::update);
        }
    }

    private UpdateControl<WanakuCapability> setErrorStatus(WanakuCapability resource, String reason, String message) {
        LOG.warnf("WanakuCapability '%s' error (%s): %s", resource.getMetadata().getName(), reason, message);

        WanakuCapabilityStatus status = new WanakuCapabilityStatus();
        Condition condition = new ConditionBuilder()
                .withType(OperatorUtil.READY_CONDITION)
                .withStatus(OperatorUtil.CONDITION_STATUS_FALSE)
                .withObservedGeneration(resource.getMetadata().getGeneration())
                .withLastTransitionTime(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .withReason(reason)
                .withMessage(message)
                .build();

        status.setConditions(List.of(condition));
        resource.setStatus(status);
        return UpdateControl.patchStatus(resource);
    }
}
