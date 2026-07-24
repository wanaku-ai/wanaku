package ai.wanaku.operator.wanaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void getImageUsesImageTagWhenImageNotSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImageTag("1.2.3");

        assertEquals("quay.io/wanaku/camel-integration-capability:1.2.3", spec.getImage());
    }

    @Test
    void getImageUsesImageTagWhenImageIsBlank() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImage("  ");
        spec.setImageTag("0.8.0");

        assertEquals("quay.io/wanaku/camel-integration-capability:0.8.0", spec.getImage());
    }

    @Test
    void getImagePrefersFullImageOverImageTag() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImage("my-registry.io/custom-cic:2.0.0");
        spec.setImageTag("1.0.0");

        assertEquals("my-registry.io/custom-cic:2.0.0", spec.getImage());
    }

    @Test
    void getImageIgnoresBlankImageTag() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImageTag("   ");

        assertEquals("quay.io/wanaku/camel-integration-capability:latest", spec.getImage());
    }

    @Test
    void getImageTagReturnsNullWhenNotSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();

        assertNull(spec.getImageTag());
    }

    @Test
    void getImageTagReturnsValueWhenSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImageTag("1.0.0");

        assertEquals("1.0.0", spec.getImageTag());
    }

    @Test
    void getImagePullPolicyReturnsNullWhenNotSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();

        assertNull(spec.getImagePullPolicy());
    }

    @Test
    void getImagePullPolicyReturnsValueWhenSet() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setImagePullPolicy("IfNotPresent");

        assertEquals("IfNotPresent", spec.getImagePullPolicy());
    }
}
