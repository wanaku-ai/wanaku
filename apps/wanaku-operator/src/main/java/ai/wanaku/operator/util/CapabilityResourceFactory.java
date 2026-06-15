package ai.wanaku.operator.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCapabilityReconciler;
import ai.wanaku.operator.wanaku.WanakuCapabilitySpec;

/**
 * Factory for creating Kubernetes resources related to WanakuCapability deployments.
 *
 * <p>Handles the creation of capability deployments, internal services,
 * and persistent volume claims.</p>
 */
public final class CapabilityResourceFactory {
    private static final Logger LOG = Logger.getLogger(CapabilityResourceFactory.class);

    public static final String WANAKU_CAPABILITY_DEPLOYMENT_FILE = "wanaku-capability-deployment.yaml";
    public static final String CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE =
            "camel-integration-capability-deployment.yaml";
    public static final String CAPABILITY_INTERNAL_SERVICE_FILE = "wanaku-capability-service-internal.yaml";
    public static final String SERVICES_VOLUME_PVC_FILE = "services-volume-pvc.yaml";

    private CapabilityResourceFactory() {}

    /**
     * Creates a PersistentVolumeClaim for capability service storage.
     *
     * @param resource the WanakuCapability custom resource
     * @param serviceName the name of the capability service
     * @return a fully configured PersistentVolumeClaim
     */
    public static PersistentVolumeClaim makeServicesVolumePVC(WanakuCapability resource, String serviceName) {
        PersistentVolumeClaim pvc = ReconcilerUtilsInternal.loadYaml(
                PersistentVolumeClaim.class, WanakuCapabilityReconciler.class, SERVICES_VOLUME_PVC_FILE);

        String deploymentName = resource.getMetadata().getName();
        String ns = resource.getMetadata().getNamespace();

        LOG.infof("Creating services-volume PVC for deployment: %s", deploymentName);
        pvc.getMetadata().setName(createVolumeClaimName(serviceName));
        pvc.getMetadata().setNamespace(ns);
        pvc.getMetadata().getLabels().put("app", deploymentName);
        pvc.getMetadata().getLabels().put("component", "wanaku-services-storage");

        pvc.addOwnerReference(resource);

        return pvc;
    }

    /**
     * Creates a volume claim name from a service name.
     *
     * @param serviceName the name of the service
     * @return the volume claim name in the format "{serviceName}-volume-claim"
     */
    public static String createVolumeClaimName(String serviceName) {
        return serviceName + "-volume-claim";
    }

    /**
     * Creates a deployment for a Wanaku-native capability.
     *
     * @param resource the WanakuCapability custom resource
     * @param context the reconciler context
     * @param capabilitiesSpec the specific capability specification
     * @return a fully configured Deployment
     */
    public static Deployment makeDesiredWanakuCapabilityDeployment(
            WanakuCapability resource,
            Context<WanakuCapability> context,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, WanakuCapabilityReconciler.class, WANAKU_CAPABILITY_DEPLOYMENT_FILE);

        return configureCapabilityDeployment(
                desiredDeployment,
                resource,
                capabilitiesSpec,
                () -> EnvironmentVariableHelper.computeWanakuCapabilitiesEnvVars(resource, capabilitiesSpec));
    }

    /**
     * Creates a deployment for a Camel Integration Capability.
     *
     * @param resource the WanakuCapability custom resource
     * @param context the reconciler context
     * @param capabilitiesSpec the specific capability specification
     * @return a fully configured Deployment
     */
    public static Deployment makeDesiredCiCCapabilityDeployment(
            WanakuCapability resource,
            Context<WanakuCapability> context,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Deployment desiredDeployment = ReconcilerUtilsInternal.loadYaml(
                Deployment.class, WanakuCapabilityReconciler.class, CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE);

        return configureCapabilityDeployment(
                desiredDeployment,
                resource,
                capabilitiesSpec,
                () -> EnvironmentVariableHelper.computeCamelIntegrationCapabilitiesEnvVars(resource, capabilitiesSpec));
    }

    /**
     * Creates an internal (ClusterIP) service for a capability.
     *
     * @param resource the WanakuCapability custom resource
     * @param capabilitiesSpec the specific capability specification
     * @return a fully configured Service
     */
    public static Service makeCapabilityInternalService(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {
        Service service = ReconcilerUtilsInternal.loadYaml(
                Service.class, WanakuCapabilityReconciler.class, CAPABILITY_INTERNAL_SERVICE_FILE);

        String serviceName = capabilitiesSpec.getName();
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

    private static Deployment configureCapabilityDeployment(
            Deployment desiredDeployment,
            WanakuCapability resource,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec,
            Supplier<List<EnvVar>> envVarSupplier) {

        String serviceName = capabilitiesSpec.getName();
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
        deploymentSpec
                .getTemplate()
                .getSpec()
                .getContainers()
                .getFirst()
                .getVolumeMounts()
                .getFirst()
                .setName(serviceName + "-volume");
        deploymentSpec.getTemplate().getSpec().getVolumes().getFirst().setName(serviceName + "-volume");
        deploymentSpec
                .getTemplate()
                .getSpec()
                .getVolumes()
                .getFirst()
                .getPersistentVolumeClaim()
                .setClaimName(createVolumeClaimName(serviceName));

        setupCapabilityContainer(resource, deploymentSpec, capabilitiesSpec, envVarSupplier);

        desiredDeployment.addOwnerReference(resource);
        return desiredDeployment;
    }

    private static void setupCapabilityContainer(
            WanakuCapability resource,
            DeploymentSpec spec,
            WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec,
            Supplier<List<EnvVar>> envVarSupplier) {
        final List<Container> containers = spec.getTemplate().getSpec().getContainers();

        final Container service = containers.get(0);
        final String serviceName = capabilitiesSpec.getName();
        service.setName(serviceName);

        String serviceImage = capabilitiesSpec.getImage();
        OperatorUtil.validateImageAllowed(serviceImage);
        service.setImage(serviceImage);

        // Resolve pull policy with fallback chain: component -> global -> default
        String componentPolicy = capabilitiesSpec.getImagePullPolicy();
        String globalPolicy = resource.getSpec().getImagePullPolicy();
        String resolvedPolicy = OperatorUtil.resolveImagePullPolicy(componentPolicy, globalPolicy);
        service.setImagePullPolicy(resolvedPolicy);

        final List<EnvVar> userDefinedVars = envVarSupplier.get();
        final List<EnvVar> templateEnvs = service.getEnv();

        for (EnvVar templateVar : templateEnvs) {
            final Optional<EnvVar> override = userDefinedVars.stream()
                    .filter(envVar -> envVar.getName().equals(templateVar.getName()))
                    .findFirst();

            if (override.isEmpty()) {
                userDefinedVars.add(templateVar);
            }
        }

        service.setEnv(userDefinedVars);
    }
}
