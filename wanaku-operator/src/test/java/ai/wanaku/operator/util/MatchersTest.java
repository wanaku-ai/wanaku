package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchersTest {

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

    @Test
    void testMatchRouteWhenExistingIsNull() {
        Route desired = new RouteBuilder()
                .withNewMetadata()
                .withName("test-route")
                .endMetadata()
                .build();

        assertFalse(Matchers.match(desired, null));
    }
}
