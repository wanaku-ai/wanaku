package ai.wanaku.backend.support;

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
        assertEquals("false", props.get("quarkus.oidc.enabled"));
        assertEquals("false", props.get("quarkus.oidc-proxy.enabled"));
        assertEquals("false", props.get("quarkus.oidc.discovery-enabled"));
        assertEquals("false", props.get("quarkus.oidc.resource-metadata.enabled"));
        assertEquals("false", props.get("quarkus.oidc.mcp.enabled"));
        assertEquals("false", props.get("quarkus.oidc.mcp.discovery-enabled"));
        assertEquals("false", props.get("quarkus.oidc.mcp.resource-metadata.enabled"));
        for (int i = 0; i < 10; i++) {
            assertEquals("false", props.get("quarkus.oidc.ns-" + i + ".enabled"));
            assertEquals("false", props.get("quarkus.oidc.ns-" + i + ".discovery-enabled"));
            assertEquals("false", props.get("quarkus.oidc.ns-" + i + ".resource-metadata.enabled"));
        }
        assertEquals("permit", props.get("quarkus.http.auth.permission.authenticated.policy"));
        assertEquals("permit", props.get("quarkus.http.auth.permission.mcp-authenticated.policy"));
        assertEquals("permit", props.get("quarkus.http.auth.permission.web.policy"));
    }

    @Test
    void getValue_returnsNull_whenAuthNotConfigured() {
        assertNull(configSource.getValue("quarkus.http.auth.permission.authenticated.policy"));
    }

    @Test
    void getValue_returnsNoAuthValue_whenAuthSetToNone() {
        System.setProperty(AUTH_PROPERTY, "none");
        assertEquals("false", configSource.getValue("quarkus.oidc.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc-proxy.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.discovery-enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.resource-metadata.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.mcp.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.mcp.discovery-enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.mcp.resource-metadata.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-0.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-0.discovery-enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-0.resource-metadata.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-9.enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-9.discovery-enabled"));
        assertEquals("false", configSource.getValue("quarkus.oidc.ns-9.resource-metadata.enabled"));
        assertEquals("permit", configSource.getValue("quarkus.http.auth.permission.authenticated.policy"));
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
        assertTrue(configSource.getPropertyNames().size() > 5);
    }

    @Test
    void isNoAuth_isCaseInsensitive() {
        System.setProperty(AUTH_PROPERTY, "NONE");
        assertEquals("false", configSource.getValue("quarkus.oidc.enabled"));

        clearAuthProperties();
        System.setProperty(AUTH_PROPERTY, "None");
        assertEquals("false", configSource.getValue("quarkus.oidc.enabled"));
    }

    @Test
    void envVar_fallsBack_whenSystemPropertyNotSet() {
        assertNull(System.getProperty(AUTH_PROPERTY));
        Map<String, String> props = configSource.getProperties();
        assertTrue(props.isEmpty());
    }
}
