package ai.wanaku.operator.wanaku;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
