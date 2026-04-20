package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCodeExecutionEngineSpec;

import static ai.wanaku.operator.assertions.OperatorAssertions.assertCondition;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertEndpointTarget;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertMetadataLabel;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertServiceLabel;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertServicePort;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCapabilitySpec;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterSpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

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

    @Test
    void resolveAuthRealmReturnsConfiguredRealm() {
        WanakuCapability capability = createCapabilityWithRealm("myrealm");
        assertEquals("myrealm", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuWhenNull() {
        WanakuCapability capability = createCapabilityWithRealm(null);
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuWhenBlank() {
        WanakuCapability capability = createCapabilityWithRealm("  ");
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuWhenEmpty() {
        WanakuCapability capability = createCapabilityWithRealm("");
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenCapabilityIsNull() {
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm((WanakuCapability) null));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenSpecIsNull() {
        WanakuCapability capability = new WanakuCapability();
        capability.setMetadata(new ObjectMetaBuilder()
                .withName("test-capability")
                .withNamespace("default")
                .build());
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenAuthIsNull() {
        WanakuCapability capability = new WanakuCapability();
        capability.setMetadata(new ObjectMetaBuilder()
                .withName("test-capability")
                .withNamespace("default")
                .build());
        capability.setSpec(new WanakuCapabilitySpec());
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(capability));
    }

    @Test
    void resolveAuthRealmReturnsConfiguredRealmForRouter() {
        WanakuRouter router = createRouterWithRealm("myrealm");
        assertEquals("myrealm", OperatorUtil.resolveAuthRealm(router));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuForRouterWhenNull() {
        WanakuRouter router = createRouterWithRealm(null);
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(router));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuForRouterWhenBlank() {
        WanakuRouter router = createRouterWithRealm(" ");
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(router));
    }

    @Test
    void resolveAuthRealmDefaultsToWanakuForRouterWhenEmpty() {
        WanakuRouter router = createRouterWithRealm("");
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(router));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenRouterIsNull() {
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm((WanakuRouter) null));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenRouterSpecIsNull() {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .build());
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(router));
    }

    @Test
    void resolveAuthRealmReturnsDefaultWhenRouterAuthIsNull() {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .build());
        router.setSpec(new WanakuRouterSpec());
        assertEquals("wanaku", OperatorUtil.resolveAuthRealm(router));
    }

    private static WanakuCapability createCapabilityWithRealm(String realm) {
        WanakuCapability capability = new WanakuCapability();
        capability.setMetadata(new ObjectMetaBuilder()
                .withName("test-capability")
                .withNamespace("default")
                .build());

        WanakuCapabilitySpec spec = new WanakuCapabilitySpec();
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");
        auth.setAuthRealm(realm);
        spec.setAuth(auth);
        capability.setSpec(spec);

        return capability;
    }

    private static WanakuRouter createRouterWithRealm(String realm) {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .build());

        WanakuRouterSpec spec = new WanakuRouterSpec();
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");
        auth.setAuthRealm(realm);
        spec.setAuth(auth);
        router.setSpec(spec);

        return router;
    }
}
