package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineSpec;

import static ai.wanaku.test.assertions.WanakuAssertions.assertCondition;
import static ai.wanaku.test.assertions.WanakuAssertions.assertEndpointTarget;
import static ai.wanaku.test.assertions.WanakuAssertions.assertMetadataLabel;
import static ai.wanaku.test.assertions.WanakuAssertions.assertServiceLabel;
import static ai.wanaku.test.assertions.WanakuAssertions.assertServicePort;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OperatorUtilTest {

    @Test
    void resolveImagePullPolicyUsesComponentPolicyWhenProvided() {
        assertEquals("Always", OperatorUtil.resolveImagePullPolicy("Always", "Never"));
    }

    @Test
    void resolveImagePullPolicyFallsBackToGlobalWhenComponentNull() {
        assertEquals("Never", OperatorUtil.resolveImagePullPolicy(null, "Never"));
    }

    @Test
    void resolveImagePullPolicyUsesDefaultWhenBothNull() {
        assertEquals("IfNotPresent", OperatorUtil.resolveImagePullPolicy(null, null));
    }

    @Test
    void resolveImagePullPolicyUsesDefaultForInvalidPolicy() {
        assertEquals("IfNotPresent", OperatorUtil.resolveImagePullPolicy("InvalidPolicy", null));
    }

    @Test
    void resolveImagePullPolicyUsesDefaultForInvalidGlobalPolicy() {
        assertEquals("IfNotPresent", OperatorUtil.resolveImagePullPolicy(null, "BadValue"));
    }

    @Test
    void resolveImagePullPolicyAcceptsAllValidValues() {
        assertEquals("Always", OperatorUtil.resolveImagePullPolicy("Always", null));
        assertEquals("IfNotPresent", OperatorUtil.resolveImagePullPolicy("IfNotPresent", null));
        assertEquals("Never", OperatorUtil.resolveImagePullPolicy("Never", null));
    }

    @Test
    void createVolumeClaimName() {
        assertEquals("my-service-volume-claim", OperatorUtil.createVolumeClaimName("my-service"));
    }

    @Test
    void readyConditionReusesTransitionTimeWhenAlreadyReady() {
        Condition previous = new ConditionBuilder()
                .withType(OperatorUtil.READY_CONDITION)
                .withStatus(OperatorUtil.CONDITION_STATUS_TRUE)
                .withLastTransitionTime("2024-01-01T00:00:00Z")
                .build();

        Condition current = OperatorUtil.readyCondition(7L, previous, "ready");

        assertEquals("2024-01-01T00:00:00Z", current.getLastTransitionTime());
        assertCondition(current, OperatorUtil.READY_CONDITION, OperatorUtil.CONDITION_STATUS_TRUE, 7L);
    }

    @Test
    void findConditionReturnsNullWhenNoMatchExists() {
        assertNull(OperatorUtil.findCondition(null, OperatorUtil.READY_CONDITION));
    }

    @Test
    void makeCodeExecutionEngineInternalServiceUsesConfiguredPortAndLabels() {
        WanakuCodeExecutionEngine resource = new WanakuCodeExecutionEngine();
        resource.setSpec(baseSpec());
        resource.getMetadata().setName("camel-code-execution-engine");
        resource.getMetadata().setNamespace("wanaku");
        resource.getMetadata().setUid("test-uid");

        Service service = OperatorUtil.makeCodeExecutionEngineInternalService(resource);

        assertEquals("camel-code-execution-engine", service.getMetadata().getName());
        assertServiceLabel(service, "serviceType", "code-execution-engine");
        assertServiceLabel(service, "serviceSubType", "camel");
        assertServiceLabel(service, "languageName", "yaml");
        assertServicePort(service, 9443);
        assertEquals(
                "camel-code-execution-engine", service.getSpec().getSelector().get("app"));
    }

    @Test
    void makeCodeExecutionEngineEndpointsUsesRemoteHostAndPort() {
        WanakuCodeExecutionEngine resource = new WanakuCodeExecutionEngine();
        WanakuCodeExecutionEngineSpec spec = baseSpec();
        spec.setDeploymentMode("remote");
        WanakuCodeExecutionEngineSpec.RemoteSpec remote = new WanakuCodeExecutionEngineSpec.RemoteSpec();
        remote.setHost("camel-engine.example.com");
        remote.setPort(9555);
        spec.setRemote(remote);
        resource.setSpec(spec);
        resource.getMetadata().setName("camel-code-execution-engine");
        resource.getMetadata().setNamespace("wanaku");
        resource.getMetadata().setUid("test-uid");

        Endpoints endpoints = OperatorUtil.makeCodeExecutionEngineEndpoints(resource);

        assertEndpointTarget(endpoints, "camel-engine.example.com", 9555);
        assertMetadataLabel(endpoints, "component", "camel-code-execution-engine");
    }

    private static WanakuCodeExecutionEngineSpec baseSpec() {
        WanakuCodeExecutionEngineSpec spec = new WanakuCodeExecutionEngineSpec();
        spec.setRouterRef("router");
        spec.setEngineType("camel");
        spec.setLanguageName("yaml");
        spec.setImage("quay.io/wanaku/camel-code-execution-engine:latest");
        spec.setPort(9443);
        return spec;
    }
}
