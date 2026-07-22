package ai.wanaku.operator.wanaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WanakuCamelRouteSpecTest {

    @Test
    void getImageReturnsDefaultWhenNull() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();

        assertEquals("quay.io/wanaku/camel-integration-capability:latest", spec.getImage());
    }

    @Test
    void getImageReturnsDefaultWhenBlank() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImage("   ");

        assertEquals("quay.io/wanaku/camel-integration-capability:latest", spec.getImage());
    }

    @Test
    void getImageReturnsCustomImageWhenSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImage("quay.io/wanaku/camel-integration-capability:0.5.0");

        assertEquals("quay.io/wanaku/camel-integration-capability:0.5.0", spec.getImage());
    }

    @Test
    void getImagePullPolicyReturnsNullWhenNotSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();

        assertEquals(null, spec.getImagePullPolicy());
    }

    @Test
    void getImagePullPolicyReturnsValueWhenSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImagePullPolicy("IfNotPresent");

        assertEquals("IfNotPresent", spec.getImagePullPolicy());
    }
}
