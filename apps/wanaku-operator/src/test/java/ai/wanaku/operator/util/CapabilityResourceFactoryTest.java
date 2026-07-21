package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCapabilitySpec;

import static ai.wanaku.operator.assertions.OperatorAssertions.assertMetadataLabel;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertServiceLabel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CapabilityResourceFactoryTest {

    private static WanakuCapability makeCapability(String crName) {
        WanakuCapability capability = new WanakuCapability();
        capability.getMetadata().setName(crName);
        capability.getMetadata().setNamespace("wanaku");
        capability.getMetadata().setUid("test-uid");
        capability.setSpec(new WanakuCapabilitySpec());
        return capability;
    }

    private static WanakuCapabilitySpec.CapabilitiesSpec makeCapabilityEntry(String name) {
        WanakuCapabilitySpec.CapabilitiesSpec spec = new WanakuCapabilitySpec.CapabilitiesSpec();
        spec.setName(name);
        spec.setImage("quay.io/wanaku/wanaku-tool-service-http:latest");
        return spec;
    }

    private static WanakuCapabilitySpec.CapabilitiesSpec makeCiCCapabilityEntry(String name) {
        WanakuCapabilitySpec.CapabilitiesSpec spec = makeCapabilityEntry(name);
        spec.setImage("quay.io/wanaku/camel-integration-capability:latest");
        spec.setType(OperatorConstants.CAMEL_INTEGRATION_CAPABILITY_TYPE);
        return spec;
    }

    @Test
    void wanakuCapabilityDeploymentUsesCapabilityNameAsAppLabel() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredWanakuCapabilityDeployment(cr, null, entry);

        assertMetadataLabel(deployment, "app", "cic-catalog-test");
        assertMetadataLabel(deployment, "app.kubernetes.io/part-of", "wanaku-capabilities");
    }

    @Test
    void wanakuCapabilityDeploymentMatchLabelsUseCapabilityName() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredWanakuCapabilityDeployment(cr, null, entry);

        assertEquals(
                "cic-catalog-test",
                deployment.getSpec().getSelector().getMatchLabels().get("app"));
    }

    @Test
    void wanakuCapabilityPodTemplateUsesCapabilityNameAsAppLabel() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredWanakuCapabilityDeployment(cr, null, entry);

        assertEquals(
                "cic-catalog-test",
                deployment.getSpec().getTemplate().getMetadata().getLabels().get("app"));
        assertEquals(
                "wanaku-capabilities",
                deployment.getSpec().getTemplate().getMetadata().getLabels().get("app.kubernetes.io/part-of"));
    }

    @Test
    void cicCapabilityDeploymentUsesCapabilityNameAsAppLabel() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCiCCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredCiCCapabilityDeployment(cr, null, entry);

        assertMetadataLabel(deployment, "app", "cic-catalog-test");
        assertMetadataLabel(deployment, "app.kubernetes.io/part-of", "wanaku-capabilities");
    }

    @Test
    void cicCapabilityDeploymentMatchLabelsUseCapabilityName() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCiCCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredCiCCapabilityDeployment(cr, null, entry);

        assertEquals(
                "cic-catalog-test",
                deployment.getSpec().getSelector().getMatchLabels().get("app"));
    }

    @Test
    void cicCapabilityPodTemplateUsesCapabilityNameAsAppLabel() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCiCCapabilityEntry("cic-catalog-test");

        Deployment deployment = CapabilityResourceFactory.makeDesiredCiCCapabilityDeployment(cr, null, entry);

        assertEquals(
                "cic-catalog-test",
                deployment.getSpec().getTemplate().getMetadata().getLabels().get("app"));
        assertEquals(
                "wanaku-capabilities",
                deployment.getSpec().getTemplate().getMetadata().getLabels().get("app.kubernetes.io/part-of"));
    }

    @Test
    void internalServiceSelectorUsesCapabilityName() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");
        WanakuCapabilitySpec.CapabilitiesSpec entry = makeCapabilityEntry("cic-catalog-test");

        Service service = CapabilityResourceFactory.makeCapabilityInternalService(cr, entry);

        assertEquals("cic-catalog-test", service.getSpec().getSelector().get("app"));
        assertServiceLabel(service, "app", "cic-catalog-test");
        assertServiceLabel(service, "app.kubernetes.io/part-of", "wanaku-capabilities");
    }

    @Test
    void pvcUsesCapabilityNameAsAppLabel() {
        WanakuCapability cr = makeCapability("wanaku-capabilities");

        PersistentVolumeClaim pvc = CapabilityResourceFactory.makeServicesVolumePVC(cr, "cic-catalog-test");

        assertMetadataLabel(pvc, "app", "cic-catalog-test");
        assertMetadataLabel(pvc, "app.kubernetes.io/part-of", "wanaku-capabilities");
    }
}
