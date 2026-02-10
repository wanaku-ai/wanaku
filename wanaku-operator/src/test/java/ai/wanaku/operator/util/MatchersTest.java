package ai.wanaku.operator.util;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import org.junit.jupiter.api.Test;

class MatchersTest {

    @Test
    void testMatchIngressWhenExistingIsNull() {
        Ingress desired = new IngressBuilder()
                .withNewMetadata()
                .withName("test-ingress")
                .endMetadata()
                .build();

        assertFalse(Matchers.match(desired, null));
    }

    @Test
    void testMatchIngressWhenNamesMatch() {
        Ingress desired = new IngressBuilder()
                .withNewMetadata()
                .withName("test-ingress")
                .endMetadata()
                .build();

        Ingress existing = new IngressBuilder()
                .withNewMetadata()
                .withName("test-ingress")
                .endMetadata()
                .build();

        assertTrue(Matchers.match(desired, existing));
    }

    @Test
    void testMatchIngressWhenNamesDiffer() {
        Ingress desired = new IngressBuilder()
                .withNewMetadata()
                .withName("test-ingress")
                .endMetadata()
                .build();

        Ingress existing = new IngressBuilder()
                .withNewMetadata()
                .withName("other-ingress")
                .endMetadata()
                .build();

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
