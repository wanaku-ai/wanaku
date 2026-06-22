package ai.wanaku.operator.util;

import ai.wanaku.operator.wanaku.WanakuTypes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorSecurityConfigTest {

    @Test
    void testClientIdIsFixed() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("wanaku-service", config.getClientId());
    }

    @Test
    void testTokenEndpointWithDefaultRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testTokenEndpointWithCustomRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        authSpec.setAuthRealm("custom-realm");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals(
                "http://keycloak:8080/realms/custom-realm/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testTokenEndpointStripsTrailingSlash() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080/");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testSecretFallsBackToDefault() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals(OperatorSecurityConfig.DEFAULT_CLIENT_SECRET, config.getSecret());
    }

    @Test
    void testIsAuthEnabledWithValidSpec() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        assertTrue(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledWithNullSpec() {
        assertFalse(OperatorSecurityConfig.isAuthEnabled(null));
    }

    @Test
    void testIsAuthEnabledWithNullAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();

        assertFalse(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledWithBlankAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("  ");

        assertFalse(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledReportsCorrectly() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertTrue(config.isAuthEnabled());
    }
}
