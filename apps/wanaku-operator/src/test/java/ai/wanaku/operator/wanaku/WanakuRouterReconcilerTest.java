package ai.wanaku.operator.wanaku;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static ai.wanaku.operator.assertions.OperatorAssertions.assertCondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WanakuRouterReconcilerTest {

    @Mock
    KubernetesClient kubernetesClient;

    @InjectMocks
    WanakuRouterReconciler reconciler;

    // ── validateSpec tests (no kube API calls) ──────────────────────────────

    @Test
    void nullIngressPassesValidation() {
        WanakuRouter resource = createRouter(null);
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
        assertNull(result.errorMessage);
    }

    @Test
    void nullTypePassesValidation() {
        WanakuRouter resource = createRouterWithType(null);
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
    }

    @Test
    void noneTypePassesValidation() {
        WanakuRouter resource = createRouterWithType(WanakuTypes.ExposureType.NONE);
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
    }

    @Test
    void routeTypePassesValidation() {
        WanakuRouter resource = createRouterWithType(WanakuTypes.ExposureType.ROUTE);
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
    }

    @Test
    void ingressTypeWithHostPassesValidation() {
        WanakuRouter resource = createRouterWithTypeAndHost(WanakuTypes.ExposureType.INGRESS, "wanaku.example.com");
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
    }

    @Test
    void ingressTypeWithNullHostFailsValidation() {
        WanakuRouter resource = createRouterWithTypeAndHost(WanakuTypes.ExposureType.INGRESS, null);
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("host"));
        assertTrue(result.errorMessage.contains("Ingress"));
    }

    @Test
    void ingressTypeWithBlankHostFailsValidation() {
        WanakuRouter resource = createRouterWithTypeAndHost(WanakuTypes.ExposureType.INGRESS, "   ");
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("host"));
    }

    @Test
    void ingressTypeWithEmptyHostFailsValidation() {
        WanakuRouter resource = createRouterWithTypeAndHost(WanakuTypes.ExposureType.INGRESS, "");
        WanakuRouterReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
    }

    // ── OpenShift guard tests (via reconcile(), mocked kubernetesClient) ────

    @Test
    void routeTypeOnNonOpenShiftSetsErrorCondition() {
        when(kubernetesClient.getApiGroup("route.openshift.io")).thenReturn(null);

        WanakuRouter resource = createRouterWithType(WanakuTypes.ExposureType.ROUTE);
        UpdateControl<WanakuRouter> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus());
        Condition condition = resource.getStatus().getConditions().getFirst();
        assertCondition(condition, "Ready", "False", 1L);
        assertTrue(condition.getMessage().contains("not an OpenShift cluster"));
        assertEquals("ValidationError", condition.getReason());
    }

    @Test
    void ingressTypeDoesNotCheckOpenShift() {
        // Ingress type should never probe the OpenShift API
        WanakuRouter resource = createRouterWithTypeAndHost(WanakuTypes.ExposureType.INGRESS, null);
        // validateSpec will fail first (missing host), so reconcile returns early
        UpdateControl<WanakuRouter> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus());
        verify(kubernetesClient, never()).getApiGroup("route.openshift.io");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static WanakuRouter createRouter(WanakuTypes.ExposureSpec ingressSpec) {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .withUid("test-uid")
                .withGeneration(1L)
                .build());
        WanakuRouterSpec spec = new WanakuRouterSpec();
        spec.setExposure(ingressSpec);
        router.setSpec(spec);
        return router;
    }

    private static WanakuRouter createRouterWithType(WanakuTypes.ExposureType type) {
        WanakuTypes.ExposureSpec ingressSpec = new WanakuTypes.ExposureSpec();
        ingressSpec.setType(type);
        return createRouter(ingressSpec);
    }

    private static WanakuRouter createRouterWithTypeAndHost(WanakuTypes.ExposureType type, String host) {
        WanakuTypes.ExposureSpec ingressSpec = new WanakuTypes.ExposureSpec();
        ingressSpec.setType(type);
        ingressSpec.setHost(host);
        return createRouter(ingressSpec);
    }
}
