package ai.wanaku.operator.util;

import java.util.Map;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchersTest {

    // ---- Ingress helpers & tests ----

    private Ingress createIngress(String name, String host, String backendService) {
        return new IngressBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost(host)
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(backendService)
                .withNewPort()
                .withNumber(8080)
                .endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }

    @Test
    void testMatchIngressWhenExistingIsNull() {
        Ingress desired = createIngress("test-ingress", "example.com", "backend-service");
        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchIngressWhenFullyMatches() {
        Ingress desired = createIngress("test-ingress", "example.com", "backend-service");
        Ingress existing = createIngress("test-ingress", "example.com", "backend-service");
        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchIngressWhenNamesDiffer() {
        Ingress desired = createIngress("test-ingress", "example.com", "backend-service");
        Ingress existing = createIngress("other-ingress", "example.com", "backend-service");
        assertFalse(Matchers.match(desired, existing));
    }

    @Test
    void testMatchIngressWhenHostsDiffer() {
        Ingress desired = createIngress("test-ingress", "example.com", "backend-service");
        Ingress existing = createIngress("test-ingress", "other.com", "backend-service");
        assertFalse(Matchers.match(desired, existing));
    }

    @Test
    void testMatchIngressWhenBackendServicesDiffer() {
        Ingress desired = createIngress("test-ingress", "example.com", "backend-service");
        Ingress existing = createIngress("test-ingress", "example.com", "other-service");
        assertFalse(Matchers.match(desired, existing));
    }

    // ---- Route tests ----

    @Test
    void testMatchRouteWhenExistingIsNull() {
        Route desired = new RouteBuilder()
                .withNewMetadata()
                .withName("test-route")
                .endMetadata()
                .build();

        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchRouteWhenFullyMatches() {
        Route desired = new RouteBuilder()
                .withNewMetadata()
                .withName("test-route")
                .endMetadata()
                .build();
        Route existing = new RouteBuilder()
                .withNewMetadata()
                .withName("test-route")
                .endMetadata()
                .build();

        assertTrue(Matchers.match(desired, existing));
    }

    // ---- Job helpers & tests ----

    private Job createJob(String templateName, String image) {
        return new JobBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewMetadata()
                .withName(templateName)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("worker")
                .withImage(image)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    @Test
    void testMatchJobWhenExistingIsNull() {
        Job desired = createJob("my-job", "busybox:latest");
        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchJobWhenFullyMatches() {
        Job desired = createJob("my-job", "busybox:latest");
        Job existing = createJob("my-job", "busybox:latest");
        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchJobWhenTemplateNamesDiffer() {
        Job desired = createJob("job-a", "busybox:latest");
        Job existing = createJob("job-b", "busybox:latest");
        assertFalse(Matchers.match(desired, existing));
    }

    @Test
    void testMatchJobWhenImagesDiffer() {
        Job desired = createJob("my-job", "busybox:latest");
        Job existing = createJob("my-job", "alpine:latest");
        assertFalse(Matchers.match(desired, existing));
    }

    // ---- Deployment helpers & tests ----

    private Deployment createDeployment(int replicas, String image) {
        return new DeploymentBuilder()
                .withNewSpec()
                .withReplicas(replicas)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withName("app")
                .withImage(image)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    @Test
    void testMatchDeploymentWhenExistingIsNull() {
        Deployment desired = createDeployment(1, "myapp:1.0");
        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchDeploymentWhenFullyMatches() {
        Deployment desired = createDeployment(3, "myapp:1.0");
        Deployment existing = createDeployment(3, "myapp:1.0");
        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchDeploymentWhenReplicasDiffer() {
        Deployment desired = createDeployment(3, "myapp:1.0");
        Deployment existing = createDeployment(5, "myapp:1.0");
        assertFalse(Matchers.match(desired, existing));
    }

    @Test
    void testMatchDeploymentWhenImagesDiffer() {
        Deployment desired = createDeployment(3, "myapp:1.0");
        Deployment existing = createDeployment(3, "myapp:2.0");
        assertFalse(Matchers.match(desired, existing));
    }

    // ---- Service helpers & tests ----

    private Service createService(String name) {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .build();
    }

    @Test
    void testMatchServiceWhenExistingIsNull() {
        Service desired = createService("my-service");
        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchServiceWhenFullyMatches() {
        Service desired = createService("my-service");
        Service existing = createService("my-service");
        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchServiceWhenNamesDiffer() {
        Service desired = createService("service-a");
        Service existing = createService("service-b");
        assertFalse(Matchers.match(desired, existing));
    }

    // ---- PersistentVolumeClaim helpers & tests ----

    private PersistentVolumeClaim createPVC(String storage, String... accessModes) {
        return new PersistentVolumeClaimBuilder()
                .withNewSpec()
                .withAccessModes(accessModes)
                .withNewResources()
                .withRequests(Map.of("storage", new Quantity(storage)))
                .endResources()
                .endSpec()
                .build();
    }

    @Test
    void testMatchPVCWhenExistingIsNull() {
        PersistentVolumeClaim desired = createPVC("1Gi", "ReadWriteOnce");
        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchPVCWhenFullyMatches() {
        PersistentVolumeClaim desired = createPVC("1Gi", "ReadWriteOnce");
        PersistentVolumeClaim existing = createPVC("1Gi", "ReadWriteOnce");
        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchPVCWhenStorageDiffers() {
        PersistentVolumeClaim desired = createPVC("1Gi", "ReadWriteOnce");
        PersistentVolumeClaim existing = createPVC("5Gi", "ReadWriteOnce");
        assertFalse(Matchers.match(desired, existing));
    }

    @Test
    void testMatchPVCWhenAccessModesDiffer() {
        PersistentVolumeClaim desired = createPVC("1Gi", "ReadWriteOnce");
        PersistentVolumeClaim existing = createPVC("1Gi", "ReadWriteMany");
        assertFalse(Matchers.match(desired, existing));
    }
}
