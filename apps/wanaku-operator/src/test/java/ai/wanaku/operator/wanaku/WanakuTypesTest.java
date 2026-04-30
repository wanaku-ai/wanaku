package ai.wanaku.operator.wanaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WanakuTypesTest {

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
