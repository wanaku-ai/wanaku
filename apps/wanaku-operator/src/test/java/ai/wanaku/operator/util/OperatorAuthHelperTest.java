package ai.wanaku.operator.util;

import java.io.IOException;
import ai.wanaku.operator.wanaku.WanakuTypes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAuthHelperTest {

    @Test
    void buildTokenEndpointUsesDefaultRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        String endpoint = OperatorAuthHelper.buildTokenEndpoint(authSpec);
        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", endpoint);
    }

    @Test
    void buildTokenEndpointUsesCustomRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        authSpec.setAuthRealm("myrealm");

        String endpoint = OperatorAuthHelper.buildTokenEndpoint(authSpec);
        assertEquals("http://keycloak:8080/realms/myrealm/protocol/openid-connect/token", endpoint);
    }

    @Test
    void buildTokenEndpointStripsTrailingSlash() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080/");

        String endpoint = OperatorAuthHelper.buildTokenEndpoint(authSpec);
        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", endpoint);
    }

    @Test
    void buildTokenEndpointDefaultsRealmWhenBlank() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        authSpec.setAuthRealm("  ");

        String endpoint = OperatorAuthHelper.buildTokenEndpoint(authSpec);
        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", endpoint);
    }

    @Test
    void parseTokenResponseExtractsAccessToken() throws IOException {
        OperatorAuthHelper helper = new OperatorAuthHelper();
        String json = "{\"access_token\":\"abc123\",\"expires_in\":300,\"token_type\":\"Bearer\"}";

        String token = helper.parseTokenResponse(json);
        assertEquals("abc123", token);
    }

    @Test
    void parseTokenResponseCachesToken() throws IOException {
        OperatorAuthHelper helper = new OperatorAuthHelper();
        String json = "{\"access_token\":\"cached-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";

        helper.parseTokenResponse(json);

        // Calling getToken should return the cached value without hitting the network
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        String cachedToken = assertDoesNotThrowGetToken(helper, authSpec);
        assertEquals("cached-token", cachedToken);
    }

    @Test
    void parseTokenResponseThrowsWhenNoAccessToken() {
        OperatorAuthHelper helper = new OperatorAuthHelper();
        String json = "{\"error\":\"invalid_client\"}";

        IOException ex = assertThrows(IOException.class, () -> helper.parseTokenResponse(json));
        assertTrue(ex.getMessage().contains("access_token"));
    }

    @Test
    void parseTokenResponseHandlesMissingExpiresIn() throws IOException {
        OperatorAuthHelper helper = new OperatorAuthHelper();
        String json = "{\"access_token\":\"no-expiry-token\",\"token_type\":\"Bearer\"}";

        String token = helper.parseTokenResponse(json);
        assertEquals("no-expiry-token", token);
    }

    @Test
    void invalidateTokenForcesRefresh() throws IOException {
        OperatorAuthHelper helper = new OperatorAuthHelper();
        String json = "{\"access_token\":\"first-token\",\"expires_in\":300}";
        helper.parseTokenResponse(json);

        helper.invalidateToken();

        // After invalidation, getToken should try to fetch (and fail since there's no real server)
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://localhost:1");

        assertThrows(IOException.class, () -> helper.getToken(authSpec));
    }

    @Test
    void isAuthEnabledReturnsFalseForNull() {
        assertFalse(OperatorAuthHelper.isAuthEnabled(null));
    }

    @Test
    void isAuthEnabledReturnsFalseForNullAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        assertFalse(OperatorAuthHelper.isAuthEnabled(authSpec));
    }

    @Test
    void isAuthEnabledReturnsFalseForBlankAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("  ");
        assertFalse(OperatorAuthHelper.isAuthEnabled(authSpec));
    }

    @Test
    void isAuthEnabledReturnsTrueWhenAuthServerSet() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        assertTrue(OperatorAuthHelper.isAuthEnabled(authSpec));
    }

    @Test
    void resolveClientSecretReturnsDefaultWhenEnvNotSet() {
        // This test verifies the fallback logic; the env var is typically not set in test
        String secret = OperatorAuthHelper.resolveClientSecret();
        assertNotNull(secret);
        assertFalse(secret.isBlank());
    }

    /**
     * Helper that calls getToken and returns the result, asserting no exception is thrown.
     */
    private static String assertDoesNotThrowGetToken(OperatorAuthHelper helper, WanakuTypes.AuthSpec authSpec) {
        try {
            return helper.getToken(authSpec);
        } catch (IOException e) {
            throw new AssertionError("getToken threw unexpected IOException", e);
        }
    }
}
