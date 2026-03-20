package ai.wanaku.backend.support;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

public class NoOidcTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.enabled", "false",
                "quarkus.oidc-proxy.enabled", "false");
    }
}
