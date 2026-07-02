package ai.wanaku.cli.main.support;

import ai.wanaku.cli.main.support.security.TokenEndpoint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenEndpointTest {

    private static final String KEYCLOAK_HOST = "http://keycloak-host";
    private static final String URL_REALMS_WANAKU = "http://keycloak-host/realms/wanaku";
    private static final String URL_REALMS_MYREALM = "http://keycloak-host/realms/myrealm";
    private static final String URL_OIDC = "http://localhost:8080/q/oidc/";
    private static final String URL_LOCALHOST = "http://localhost:8080";

    @Test
    void forDiscovery_withRealm_returnsKeycloakNativePath() {
        assertEquals(URL_REALMS_WANAKU, TokenEndpoint.forDiscovery(KEYCLOAK_HOST, "wanaku"));
    }

    @Test
    void forDiscovery_withRealm_stripsTrailingSlash() {
        assertEquals(URL_REALMS_MYREALM, TokenEndpoint.forDiscovery(KEYCLOAK_HOST + "/", "myrealm"));
    }

    @Test
    void forDiscovery_withRealm_stripsRealmWhitespace() {
        assertEquals(URL_REALMS_MYREALM, TokenEndpoint.forDiscovery(KEYCLOAK_HOST, "  myrealm  "));
    }

    @Test
    void forDiscovery_nullRealm_fallsBackToProxyPath() {
        assertEquals(URL_OIDC, TokenEndpoint.forDiscovery(URL_LOCALHOST, null));
    }

    @Test
    void forDiscovery_blankRealm_fallsBackToProxyPath() {
        assertEquals(URL_OIDC, TokenEndpoint.forDiscovery(URL_LOCALHOST, "   "));
    }
}
