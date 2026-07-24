package ai.wanaku.operator.wanaku;

import java.lang.reflect.Field;
import java.util.Map;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import ai.wanaku.operator.util.CapabilityResourceFactory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WanakuCamelRouteReconcilerTest {

    @Test
    void isDeploymentReadyReturnsFalseWhenDeploymentIsNull() {
        assertFalse(WanakuCamelRouteReconciler.isDeploymentReady(null));
    }

    @Test
    void isDeploymentReadyReturnsFalseWhenStatusIsNull() {
        Deployment deployment = new Deployment();
        assertFalse(WanakuCamelRouteReconciler.isDeploymentReady(deployment));
    }

    @Test
    void isDeploymentReadyReturnsFalseWhenReadyReplicasIsNull() {
        Deployment deployment = new Deployment();
        DeploymentStatus status = new DeploymentStatus();
        status.setReadyReplicas(null);
        deployment.setStatus(status);
        assertFalse(WanakuCamelRouteReconciler.isDeploymentReady(deployment));
    }

    @Test
    void isDeploymentReadyReturnsFalseWhenReadyReplicasIsZero() {
        Deployment deployment = new Deployment();
        DeploymentStatus status = new DeploymentStatus();
        status.setReadyReplicas(0);
        deployment.setStatus(status);
        assertFalse(WanakuCamelRouteReconciler.isDeploymentReady(deployment));
    }

    @Test
    void isDeploymentReadyReturnsTrueWhenReadyReplicasIsPositive() {
        Deployment deployment = new Deployment();
        DeploymentStatus status = new DeploymentStatus();
        status.setReadyReplicas(1);
        deployment.setStatus(status);
        assertTrue(WanakuCamelRouteReconciler.isDeploymentReady(deployment));
    }

    @Test
    void isCicReadyReturnsFalseWhenDeploymentNotFound() throws Exception {
        WanakuCamelRouteReconciler reconciler = new WanakuCamelRouteReconciler();
        KubernetesClient mockClient = mockKubernetesClient("test-ns", "test-cic", null);
        setKubernetesClient(reconciler, mockClient);

        assertFalse(reconciler.isCicReady("test-cic", "test-ns"));
    }

    @Test
    void isCicReadyReturnsFalseWhenDeploymentNotReady() throws Exception {
        WanakuCamelRouteReconciler reconciler = new WanakuCamelRouteReconciler();

        Deployment deployment = new Deployment();
        DeploymentStatus status = new DeploymentStatus();
        status.setReadyReplicas(0);
        deployment.setStatus(status);

        KubernetesClient mockClient = mockKubernetesClient("test-ns", "test-cic", deployment);
        setKubernetesClient(reconciler, mockClient);

        assertFalse(reconciler.isCicReady("test-cic", "test-ns"));
    }

    @Test
    void isCicReadyReturnsTrueWhenDeploymentReady() throws Exception {
        WanakuCamelRouteReconciler reconciler = new WanakuCamelRouteReconciler();

        Deployment deployment = new Deployment();
        DeploymentStatus status = new DeploymentStatus();
        status.setReadyReplicas(1);
        deployment.setStatus(status);

        KubernetesClient mockClient = mockKubernetesClient("test-ns", "test-cic", deployment);
        setKubernetesClient(reconciler, mockClient);

        assertTrue(reconciler.isCicReady("test-cic", "test-ns"));
    }

    @SuppressWarnings("unchecked")
    private static KubernetesClient mockKubernetesClient(String namespace, String name, Deployment result) {
        KubernetesClient client = mock(KubernetesClient.class);
        AppsAPIGroupDSL apps = mock(AppsAPIGroupDSL.class);
        MixedOperation deployments = mock(MixedOperation.class);
        MixedOperation namespacedDeployments = mock(MixedOperation.class);
        RollableScalableResource namedResource = mock(RollableScalableResource.class);

        when(client.apps()).thenReturn(apps);
        when(apps.deployments()).thenReturn(deployments);
        when(deployments.inNamespace(namespace)).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName(name)).thenReturn(namedResource);
        when(namedResource.get()).thenReturn(result);

        return client;
    }

    // ── Annotation env var tests ──────────────────────────────────────────────

    @Test
    void annotationEnvVarAddsNewVarToCicDeployment() {
        WanakuCamelRouteReconciler reconciler = new WanakuCamelRouteReconciler();
        WanakuCamelRoute resource = makeCamelRoute("my-route");
        resource.getMetadata().setAnnotations(Map.of("env.wanaku.ai/MY_CIC_VAR", "cic_value"));

        Deployment deployment = loadCicTemplate();
        reconciler.configureCicDeployment(
                deployment, resource, "my-route", "my-route-cic", "wanaku", "http://internal-router:8080", null);

        assertEquals("cic_value", getEnvValue(deployment, "MY_CIC_VAR"));
    }

    @Test
    void annotationEnvVarOverridesCicStandardVar() {
        WanakuCamelRouteReconciler reconciler = new WanakuCamelRouteReconciler();
        WanakuCamelRoute resource = makeCamelRoute("my-route");
        // SERVICE_NAME is one of the 8 standard CIC env vars
        resource.getMetadata().setAnnotations(Map.of("env.wanaku.ai/SERVICE_NAME", "overridden"));

        Deployment deployment = loadCicTemplate();
        reconciler.configureCicDeployment(
                deployment, resource, "my-route", "my-route-cic", "wanaku", "http://internal-router:8080", null);

        assertEquals("overridden", getEnvValue(deployment, "SERVICE_NAME"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static WanakuCamelRoute makeCamelRoute(String name) {
        WanakuCamelRoute resource = new WanakuCamelRoute();
        resource.getMetadata().setName(name);
        resource.getMetadata().setNamespace("wanaku");
        resource.getMetadata().setUid("test-uid");
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImage("quay.io/wanaku/camel-integration-capability:latest");
        resource.setSpec(spec);
        return resource;
    }

    private static Deployment loadCicTemplate() {
        return ReconcilerUtilsInternal.loadYaml(
                Deployment.class,
                WanakuCamelRouteReconciler.class,
                CapabilityResourceFactory.CAMEL_INTEGRATION_CAPABILITY_DEPLOYMENT_FILE);
    }

    private static String getEnvValue(Deployment deployment, String envName) {
        return deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .findFirst()
                .flatMap(c -> c.getEnv().stream()
                        .filter(e -> e.getName().equals(envName))
                        .findFirst())
                .map(EnvVar::getValue)
                .orElse(null);
    }

    private static void setKubernetesClient(WanakuCamelRouteReconciler reconciler, KubernetesClient client)
            throws Exception {
        Field field = WanakuCamelRouteReconciler.class.getDeclaredField("kubernetesClient");
        field.setAccessible(true);
        field.set(reconciler, client);
    }
}
