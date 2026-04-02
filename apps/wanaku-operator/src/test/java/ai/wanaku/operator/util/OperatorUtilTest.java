package ai.wanaku.operator.util;

import ai.wanaku.operator.wanaku.StatusCondition;

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
        StatusCondition previous = new StatusCondition();
        previous.setType(OperatorUtil.READY_CONDITION);
        previous.setStatus(OperatorUtil.CONDITION_STATUS_TRUE);
        previous.setLastTransitionTime("2024-01-01T00:00:00Z");

        StatusCondition current = OperatorUtil.readyCondition(7L, previous, "ready");

        assertEquals("2024-01-01T00:00:00Z", current.getLastTransitionTime());
        assertEquals(OperatorUtil.READY_CONDITION, current.getType());
        assertEquals(OperatorUtil.CONDITION_STATUS_TRUE, current.getStatus());
        assertEquals(7L, current.getObservedGeneration());
    }

    @Test
    void findConditionReturnsNullWhenNoMatchExists() {
        assertNull(OperatorUtil.findCondition(null, OperatorUtil.READY_CONDITION));
    }
}
