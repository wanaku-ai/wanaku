package ai.wanaku.backend.api.v1.oidc;

import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OAuthAuthorizationServerWellKnownResourceIT.Profile.class)
class OAuthAuthorizationServerWellKnownResourceIT {

    @ConfigProperty(name = "auth.proxy")
    String authProxy;

    @ConfigProperty(name = "quarkus.oidc-proxy.root-path")
    String oidcProxyRootPath;

    @Test
    void forwardsOpenIdConfiguration() {
        given().when()
                .get("/.well-known/oauth-authorization-server")
                .then()
                .statusCode(200)
                .body("issuer", equalTo(authProxy + oidcProxyRootPath));
    }

    @Test
    void forwardsOpenIdConfigurationForTenant() {
        given().when()
                .get("/.well-known/oauth-authorization-server/mcp")
                .then()
                .statusCode(200)
                .body("issuer", equalTo(authProxy + oidcProxyRootPath));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc-proxy.enabled", "false",
                    "quarkus.oidc.enabled", "false",
                    "quarkus.oidc-proxy.root-path", "/test-oidc");
        }
    }
}
