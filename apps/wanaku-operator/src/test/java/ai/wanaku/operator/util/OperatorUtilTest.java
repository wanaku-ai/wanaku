package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
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
        assertEquals(OperatorUtil.READY_CONDITION, current.getType());
        assertEquals(OperatorUtil.CONDITION_STATUS_TRUE, current.getStatus());
        assertEquals(7L, current.getObservedGeneration());
    }

    @Test
    void findConditionReturnsNullWhenNoMatchExists() {
        assertNull(OperatorUtil.findCondition(null, OperatorUtil.READY_CONDITION));
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
