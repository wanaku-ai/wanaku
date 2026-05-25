package ai.wanaku.core.persistence.infinispan.remote;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

public class RemoteInfinispanTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.devservices.enabled", "true",
                "quarkus.infinispan-client.devservices.enabled", "true",
                "quarkus.infinispan-client.devservices.image-name", "infinispan/server:16.0");
    }
}
