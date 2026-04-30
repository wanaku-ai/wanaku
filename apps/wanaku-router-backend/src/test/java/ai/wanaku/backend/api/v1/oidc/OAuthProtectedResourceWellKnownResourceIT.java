package ai.wanaku.backend.api.v1.oidc;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

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
                .contentType(JSON)
                .body("resource", endsWith("/ns-1/mcp"))
                .body("authorization_servers", hasItem(endsWith("/q/oidc")));
    }

    @Test
    void returnsCorrectResourceUrlForDifferentNamespaces() {
        given().when()
                .get("/.well-known/oauth-protected-resource/ns-5/mcp")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("resource", endsWith("/ns-5/mcp"))
                .body("authorization_servers", hasItem(endsWith("/q/oidc")));
    }

    @Test
    void respectsForwardedProtoHeader() {
        given().header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "wanaku-backend.dev")
                .when()
                .get("/.well-known/oauth-protected-resource/ns-1/mcp")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("resource", startsWith("https://wanaku-backend.dev/"))
                .body("resource", endsWith("/ns-1/mcp"))
                .body("authorization_servers", hasItem(startsWith("https://wanaku-backend.dev/")));
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
