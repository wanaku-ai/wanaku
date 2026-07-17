package ai.wanaku.backend.support;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

public class DegradedOidcTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "wanaku.http.auth", "keycloak",
                "quarkus.oidc.enabled", "true",
                "quarkus.oidc.auth-server-url", "http://localhost:1/realms/nonexistent",
                "quarkus.oidc.mcp.auth-server-url", "http://localhost:1/realms/nonexistent",
                "auth.server", "http://localhost:1",
                "quarkus.oidc.connection-delay", "1S",
                "quarkus.oidc.mcp.connection-delay", "1S");
    }
}
