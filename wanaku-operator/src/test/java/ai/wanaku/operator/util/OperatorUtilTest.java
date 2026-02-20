package ai.wanaku.operator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
