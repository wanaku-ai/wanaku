package ai.wanaku.operator.wanaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WanakuTypesTest {

    // ── ExposureType ─────────────────────────────────────────────────────────

    @Test
    void exposureTypeFromValueNullReturnsNull() {
        assertNull(WanakuTypes.ExposureType.fromValue(null));
    }

    @Test
    void exposureTypeFromValueCaseInsensitive() {
        assertEquals(WanakuTypes.ExposureType.ROUTE, WanakuTypes.ExposureType.fromValue("route"));
        assertEquals(WanakuTypes.ExposureType.ROUTE, WanakuTypes.ExposureType.fromValue("ROUTE"));
        assertEquals(WanakuTypes.ExposureType.ROUTE, WanakuTypes.ExposureType.fromValue("Route"));
        assertEquals(WanakuTypes.ExposureType.INGRESS, WanakuTypes.ExposureType.fromValue("ingress"));
        assertEquals(WanakuTypes.ExposureType.INGRESS, WanakuTypes.ExposureType.fromValue("INGRESS"));
        assertEquals(WanakuTypes.ExposureType.NONE, WanakuTypes.ExposureType.fromValue("none"));
        assertEquals(WanakuTypes.ExposureType.NONE, WanakuTypes.ExposureType.fromValue("nOnE"));
    }

    @Test
    void exposureTypeFromValueInvalidThrows() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> WanakuTypes.ExposureType.fromValue("banana"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("banana"));
    }

    // ── TlsTermination ───────────────────────────────────────────────────────

    @Test
    void tlsTerminationFromValueNullReturnsNull() {
        assertNull(WanakuTypes.TlsTermination.fromValue(null));
    }

    @Test
    void tlsTerminationFromValueCaseInsensitive() {
        assertEquals(WanakuTypes.TlsTermination.EDGE, WanakuTypes.TlsTermination.fromValue("edge"));
        assertEquals(WanakuTypes.TlsTermination.EDGE, WanakuTypes.TlsTermination.fromValue("EDGE"));
        assertEquals(WanakuTypes.TlsTermination.PASSTHROUGH, WanakuTypes.TlsTermination.fromValue("passthrough"));
        assertEquals(WanakuTypes.TlsTermination.REENCRYPT, WanakuTypes.TlsTermination.fromValue("reencrypt"));
        assertEquals(WanakuTypes.TlsTermination.REENCRYPT, WanakuTypes.TlsTermination.fromValue("REENCRYPT"));
    }

    @Test
    void tlsTerminationFromValueInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> WanakuTypes.TlsTermination.fromValue("invalid"));
    }

    @Test
    void tlsTerminationToValueIsLowercase() {
        assertEquals("edge", WanakuTypes.TlsTermination.EDGE.toValue());
        assertEquals("passthrough", WanakuTypes.TlsTermination.PASSTHROUGH.toValue());
        assertEquals("reencrypt", WanakuTypes.TlsTermination.REENCRYPT.toValue());
    }

    // ── InsecureEdgeTerminationPolicy ─────────────────────────────────────────

    @Test
    void insecureEdgeTerminationPolicyFromValueNullReturnsNull() {
        assertNull(WanakuTypes.InsecureEdgeTerminationPolicy.fromValue(null));
    }

    @Test
    void insecureEdgeTerminationPolicyFromValueCaseInsensitive() {
        assertEquals(
                WanakuTypes.InsecureEdgeTerminationPolicy.ALLOW,
                WanakuTypes.InsecureEdgeTerminationPolicy.fromValue("allow"));
        assertEquals(
                WanakuTypes.InsecureEdgeTerminationPolicy.ALLOW,
                WanakuTypes.InsecureEdgeTerminationPolicy.fromValue("ALLOW"));
        assertEquals(
                WanakuTypes.InsecureEdgeTerminationPolicy.NONE,
                WanakuTypes.InsecureEdgeTerminationPolicy.fromValue("none"));
        assertEquals(
                WanakuTypes.InsecureEdgeTerminationPolicy.REDIRECT,
                WanakuTypes.InsecureEdgeTerminationPolicy.fromValue("Redirect"));
    }

    @Test
    void insecureEdgeTerminationPolicyFromValueInvalidThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> WanakuTypes.InsecureEdgeTerminationPolicy.fromValue("invalid"));
    }

    @Test
    void insecureEdgeTerminationPolicyToValueIsTitleCase() {
        assertEquals("Allow", WanakuTypes.InsecureEdgeTerminationPolicy.ALLOW.toValue());
        assertEquals("None", WanakuTypes.InsecureEdgeTerminationPolicy.NONE.toValue());
        assertEquals("Redirect", WanakuTypes.InsecureEdgeTerminationPolicy.REDIRECT.toValue());
    }

    // ── AuthSpec (existing tests below) ─────────────────────────────────────

    @Test
    void authRealmDefaultsToNull() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        assertNull(auth.getAuthRealm());
    }

    @Test
    void authRealmGetterAndSetter() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthRealm("myrealm");
        assertEquals("myrealm", auth.getAuthRealm());
    }

    @Test
    void authRealmCanBeSetToNull() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthRealm("myrealm");
        auth.setAuthRealm(null);
        assertNull(auth.getAuthRealm());
    }

    @Test
    void authServerGetterAndSetter() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");
        assertEquals("http://keycloak:8080", auth.getAuthServer());
    }

    @Test
    void authProxyGetterAndSetter() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthProxy("http://proxy:8080");
        assertEquals("http://proxy:8080", auth.getAuthProxy());
    }

    @Test
    void authRealmIsIndependentOfOtherFields() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://server:8080");
        auth.setAuthProxy("http://proxy:8080");
        auth.setAuthRealm("custom-realm");
        assertEquals("http://server:8080", auth.getAuthServer());
        assertEquals("http://proxy:8080", auth.getAuthProxy());
        assertEquals("custom-realm", auth.getAuthRealm());
    }
}
