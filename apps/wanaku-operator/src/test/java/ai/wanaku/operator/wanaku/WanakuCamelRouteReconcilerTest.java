package ai.wanaku.operator.wanaku;

import java.lang.reflect.Field;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import org.junit.jupiter.api.Test;
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

    private static void setKubernetesClient(WanakuCamelRouteReconciler reconciler, KubernetesClient client)
            throws Exception {
        Field field = WanakuCamelRouteReconciler.class.getDeclaredField("kubernetesClient");
        field.setAccessible(true);
        field.set(reconciler, client);
    }
}
