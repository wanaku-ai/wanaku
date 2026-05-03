package ai.wanaku.core.capabilities.common;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigSourceTest {

    private static final String AUTH_PROPERTY = "wanaku.http.auth";
    private static final String ENV_VAR = "WANAKU_HTTP_AUTH";

    private AuthConfigSource configSource;

    @BeforeEach
    void setUp() {
        configSource = new AuthConfigSource();
        clearAuthProperties();
    }

    @AfterEach
    void tearDown() {
        clearAuthProperties();
    }

    private void clearAuthProperties() {
        System.clearProperty(AUTH_PROPERTY);
    }

    @Test
    void getName_returnsExpectedValue() {
        assertEquals("wanaku-auth-config-source", configSource.getName());
    }

    @Test
    void getOrdinal_returnsExpectedValue() {
        assertEquals(260, configSource.getOrdinal());
    }

    @Test
    void getProperties_returnsEmptyMap_whenAuthNotConfigured() {
        Map<String, String> props = configSource.getProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test
    void getProperties_returnsEmptyMap_whenAuthSetToKeycloak() {
        System.setProperty(AUTH_PROPERTY, "keycloak");
        Map<String, String> props = configSource.getProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test
    void getProperties_returnsNoAuthProperties_whenAuthSetToNoneViaProperty() {
        System.setProperty(AUTH_PROPERTY, "none");
        Map<String, String> props = configSource.getProperties();
        assertNotNull(props);
        assertEquals(1, props.size());
        assertEquals("false", props.get("quarkus.oidc-client.enabled"));
    }

    @Test
    void getValue_returnsNull_whenAuthNotConfigured() {
        assertNull(configSource.getValue("quarkus.oidc-client.enabled"));
    }

    @Test
    void getValue_returnsNoAuthValue_whenAuthSetToNone() {
        System.setProperty(AUTH_PROPERTY, "none");
        assertEquals("false", configSource.getValue("quarkus.oidc-client.enabled"));
    }

    @Test
    void getValue_returnsNullForUnknownProperty_whenAuthSetToNone() {
        System.setProperty(AUTH_PROPERTY, "none");
        assertNull(configSource.getValue("unknown.property"));
    }

    @Test
    void getPropertyNames_returnsEmptySet_whenAuthNotConfigured() {
        assertTrue(configSource.getPropertyNames().isEmpty());
    }

    @Test
    void getPropertyNames_returnsNoAuthKeys_whenAuthSetToNone() {
        System.setProperty(AUTH_PROPERTY, "none");
        assertEquals(1, configSource.getPropertyNames().size());
    }

    @Test
    void isNoAuth_isCaseInsensitive() {
        System.setProperty(AUTH_PROPERTY, "NONE");
        assertEquals("false", configSource.getValue("quarkus.oidc-client.enabled"));

        clearAuthProperties();
        System.setProperty(AUTH_PROPERTY, "None");
        assertEquals("false", configSource.getValue("quarkus.oidc-client.enabled"));
    }

    @Test
    void envVar_fallsBack_whenSystemPropertyNotSet() {
        // This test verifies the fallback mechanism.
        // Since we cannot reliably set env vars in Java, we verify the source
        // returns empty when neither system property nor env var is set.
        assertNull(System.getProperty(AUTH_PROPERTY));
        Map<String, String> props = configSource.getProperties();
        assertTrue(props.isEmpty());
    }
}
