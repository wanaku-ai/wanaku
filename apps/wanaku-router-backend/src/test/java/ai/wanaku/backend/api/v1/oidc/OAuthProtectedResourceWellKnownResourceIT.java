package ai.wanaku.backend.api.v1.oidc;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(OAuthProtectedResourceWellKnownResourceIT.Profile.class)
class OAuthProtectedResourceWellKnownResourceIT {

    @Test
    void returnsResourceMetadataForNamespace() {
        given().when()
                .get("/.well-known/oauth-protected-resource/ns-1/mcp")
                .then()
                .statusCode(200)
                .body(containsString("\"resource\""))
                .body(containsString("/ns-1/mcp"))
                .body(containsString("\"authorization_servers\""))
                .body(containsString("/q/oidc"));
    }

    @Test
    void returnsCorrectResourceUrlForDifferentNamespaces() {
        given().when()
                .get("/.well-known/oauth-protected-resource/ns-5/mcp")
                .then()
                .statusCode(200)
                .body(containsString("/ns-5/mcp"))
                .body(containsString("\"authorization_servers\""));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc-proxy.enabled", "false",
                    "quarkus.oidc.enabled", "false",
                    "quarkus.oidc-proxy.root-path", "/q/oidc");
        }
    }
}
